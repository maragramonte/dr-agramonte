# Despliegue en un VPS propio

Guía para poner Dr. Agramonte en producción sobre un servidor virtual con Docker,
con HTTPS y dominio propio. Es **agnóstica de proveedor**: los comandos son los
mismos en Hetzner, IONOS, DigitalOcean, OVH, Contabo o cualquier otro que dé una
máquina Linux con IP fija.

La app no depende de ningún servicio propietario: si el proveedor falla, copias el
repositorio y el último volcado de la base de datos a otro servidor y en media
hora estás en marcha otra vez.

**Coste orientativo:** 6-7 EUR/mes por el VPS, fijos, más el dominio que ya tienes.

---

## 0. Qué vas a montar

```
        Internet
           |  443 (HTTPS)
     +-----v-----+
     |   Caddy   |  certificado Let's Encrypt automático
     |  (proxy)  |  apex --301--> www
     +-----v-----+
           |  8080 (red interna de Docker)
     +-----v-----+
     |    app    |  Spring Boot: sirve la API y el frontend
     |           |  (imagen del Dockerfile de la raíz)
     +-----v-----+
           |  5432 (red interna, sin salida a Internet)
     +-----v-----+
     | postgres  |  datos en un volumen de Docker
     +-----------+
```

Tres contenedores definidos en `docker-compose.prod.yml`. Nada escucha en la IP
pública salvo Caddy: la base de datos no publica puerto y la aplicación tampoco.

**Ficheros implicados:**

| Fichero | Para qué |
|---|---|
| `docker-compose.prod.yml` | Los tres servicios de producción |
| `Caddyfile` | HTTPS, redirección apex a www, cabeceras de seguridad |
| `.env.prod.example` | Plantilla de variables; se copia a `.env.prod` **en el servidor** |
| `scripts/backup-db.sh` | Volcado comprimido con rotación |
| `scripts/deploy.sh` | Actualizar la app (copia previa + rebuild) |

El `docker-compose.yml` de desarrollo **no se toca**: sigue sirviendo para
trabajar en local con nginx + backend + Postgres por separado.

---

## 1. Lo que necesitas antes de empezar

- Un **VPS** con Ubuntu 24.04 LTS (o Debian 12). Mínimo **2 GB de RAM** y 20 GB
  de disco; con 4 GB vas holgada. La JVM es lo que más memoria pide.
- La **IP pública** del servidor y acceso SSH.
- El dominio `dragramonte.com` con acceso al **editor de zona DNS** (IONOS).
- El repositorio en GitHub (`maragramonte/dr-agramonte`), ya lo tienes.

### El servidor elegido

Todo lo que sigue funciona en cualquier proveedor, pero para no dejarlo en
abstracto, este es el que usa el proyecto:

| | |
|---|---|
| **Proveedor** | Hetzner Cloud |
| **Plan** | **CX23** — 2 vCPU (x86), 4 GB RAM, 40 GB NVMe, 20 TB de tráfico |
| **Ubicación** | Falkenstein o Núremberg (Alemania, UE) |
| **Sistema** | Ubuntu 24.04 LTS |
| **Precio** | ~5,49 €/mes + 0,50 € de la IPv4, IVA aparte (agosto 2026) |
| **Extra recomendado** | Copias automáticas del proveedor: +20 % (~1,10 €/mes) |
| **IP pública** | _(anotar aquí al contratar)_ |

**Por qué 4 GB y no 2.** `scripts/deploy.sh` compila Maven **en el propio
servidor**. El build se come cerca de 1 GB y coincide con la JVM anterior aún
en marcha, Postgres y Caddy. Con 2 GB el despliegue puede morir por falta de
memoria justo mientras actualizas. Con 4 GB sobra y el `mem_limit: 1g` del
compose se queda como está.

**Las copias automáticas del proveedor no sustituyen a `backup-db.sh`, lo
complementan:** son instantáneas del disco entero, para cuando lo que pierdes
es la máquina y no solo los datos.

**Al contratar:**

- Sube tu **clave SSH en el propio formulario de creación**, no después: el
  servidor nace sin acceso por contraseña.
- **Ubicación en la UE**, no negociable: aquí se tratan datos de salud.
- **Firma el contrato de encargado del tratamiento** (DPA) desde el panel del
  proveedor. Los datos de salud son categoría especial del RGPD y sin ese
  contrato con quien aloja el servidor el cumplimiento cojea.
- Hetzner pide a veces **verificación de identidad** en el alta y tarda unas
  horas en activar la cuenta.

**Si algún día cambias de proveedor:** solo hay que rehacer los apartados 2, 3 y
6 en la máquina nueva, restaurar el último volcado y cambiar la IP de los dos
registros `A`. Nada del código ni de la configuración depende de Hetzner.

> **Si vienes de otro alojamiento, rescata los datos antes de apagarlo.** Mientras
> su Postgres siga vivo, saca un volcado usando la URL de conexión pública que dé
> su panel:
>
> `pg_dump "postgresql://usuario:clave@host:puerto/basededatos" > backups/desde-el-anterior.sql`
>
> Si ya no está, no pasa nada: Flyway recrea el esquema vacío en el primer arranque.

---

## 2. Preparar el servidor

Conéctate por SSH como `root` (o con el usuario que dé el proveedor):

```bash
ssh root@LA_IP_DEL_SERVIDOR
```

### 2.1 Actualizar y crear un usuario propio

Trabajar como `root` a diario es mala idea: cualquier error se lleva la máquina.

```bash
apt update && apt upgrade -y
adduser mar                 # pedirá contraseña
usermod -aG sudo mar
rsync --archive --chown=mar:mar ~/.ssh /home/mar    # copia tu clave SSH
```

Sal y vuelve a entrar como el usuario nuevo: `ssh mar@LA_IP_DEL_SERVIDOR`.

**Comprueba que entras con la clave antes de seguir.** El paso siguiente cierra la
puerta de las contraseñas, y si la clave no funciona te quedas fuera del servidor.

Con la clave ya probada, desactiva el acceso por contraseña: un servidor con datos
de salud y una IP pública recibe intentos de fuerza bruta desde el primer día.

```bash
sudo sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
sudo sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin prohibit-password/' /etc/ssh/sshd_config
sudo systemctl restart ssh
```

En Ubuntu 24.04 puede haber ficheros en `/etc/ssh/sshd_config.d/` que manden sobre
lo anterior; compruébalo con `sudo sshd -T | grep -E 'passwordauthentication|permitrootlogin'`,
que enseña la configuración efectiva. **Deja la sesión actual abierta** mientras
verificas desde otra terminal que sigues entrando.

### 2.2 Cortafuegos

Solo tres puertos abiertos: SSH y los dos de web.

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 443/udp
sudo ufw --force enable
sudo ufw status
```

> **Ojo con `ufw` y Docker:** Docker escribe sus propias reglas de `iptables` y los
> puertos que publica un contenedor quedan accesibles aunque `ufw` diga lo
> contrario. Aquí no es un problema **porque solo Caddy publica puertos** (80 y
> 443): la aplicación usa `expose` y Postgres no publica nada, así que ninguno de
> los dos asoma a Internet. Si algún día añades un `ports:` a otro servicio,
> recuerda que `ufw` no te va a proteger de él.

### 2.3 Instalar Docker

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
```

Cierra la sesión SSH y vuelve a entrar para que el grupo `docker` tenga efecto.
Comprueba con `docker run --rm hello-world`.

**Limita el tamaño de los logs.** Por omisión Docker guarda la salida de cada
contenedor en un fichero JSON que crece sin tope: en un disco de 40 GB, meses de
logs de Spring acaban llenándolo y tumbando la web por algo tan tonto como eso.

```bash
sudo tee /etc/docker/daemon.json > /dev/null <<'JSON'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
JSON
sudo systemctl restart docker
```

Son 30 MB de log como mucho por contenedor. El límite se aplica a los contenedores
que se creen a partir de ahora, así que hazlo **antes** del primer arranque.

### 2.4 Actualizaciones de seguridad automáticas

```bash
sudo apt install -y unattended-upgrades
sudo dpkg-reconfigure -plow unattended-upgrades
```

---

## 3. Traer el proyecto

```bash
cd ~
git clone https://github.com/maragramonte/dr-agramonte.git
cd dr-agramonte
```

Si el repositorio es privado, GitHub pedirá credenciales: usa un *personal access
token* o añade una clave SSH del servidor a tu cuenta.

---

## 4. Configurar las variables

```bash
cp .env.prod.example .env.prod
nano .env.prod
```

Genera los dos secretos obligatorios y pégalos:

```bash
openssl rand -base64 32     # POSTGRES_PASSWORD
openssl rand -base64 48     # JWT_SECRET
```

Protege el fichero, que lleva las claves en claro:

```bash
chmod 600 .env.prod
```

`.env.prod` está en `.gitignore`: **nunca** se sube al repositorio. Guarda una
copia de la contraseña de Postgres en tu gestor de contraseñas: si la pierdes y
el volumen de datos ya existe, el contenedor no podrá abrirlo.

---

## 5. Apuntar el dominio

Con una IP fija, los DNS son dos registros `A` y se acabó. **Nada de CNAME y nada del asistente de "Redirección" de IONOS**,
que es justo lo que sobrescribía el registro `www` una y otra vez.

En IONOS, en *Dominios* -> `dragramonte.com` -> **Editar zona DNS**:

1. **Quita la Redirección** si sigue activa: en *Ajustar destino*, elige el modo
   DNS normal, no "Redirección". Mientras haya un reenvío, IONOS reescribirá los
   registros por su cuenta.
2. Borra los registros `A`, `AAAA` y `CNAME` que existan para `@` y para `www`
   (incluido cualquier `CNAME` de `www` que apuntara al alojamiento anterior).
3. Crea estos dos:

   | Tipo | Host | Valor | TTL |
   |---|---|---|---|
   | `A` | `@` | `LA_IP_DEL_SERVIDOR` | 3600 |
   | `A` | `www` | `LA_IP_DEL_SERVIDOR` | 3600 |

4. Si el VPS tiene IPv6, añade además dos `AAAA` con esa dirección. Si no la
   tiene, **no dejes ningún `AAAA`**: un AAAA que no responde provoca errores
   intermitentes difíciles de diagnosticar.

Comprueba la propagación (de minutos a un par de horas):

```bash
dig +short www.dragramonte.com @1.1.1.1
dig +short dragramonte.com @1.1.1.1
```

Las dos tienen que devolver la IP del servidor y nada más.

> **Ojo con tu red local:** tu ISP intercepta las consultas DNS y a veces devuelve
> IPs de IONOS aunque el registro esté bien. Si dudas, comprueba desde el propio
> VPS o en <https://dnschecker.org>.

**No sigas al paso 6 hasta que el DNS resuelva a la IP nueva:** Caddy pide el
certificado en el primer arranque y Let's Encrypt necesita que el dominio ya
apunte al servidor. Si falla, hay un límite de 5 intentos por hora.

Por último, si vienes de otro alojamiento, quita allí el dominio personalizado
para que no queden dos sitios reclamando el mismo nombre.

---

## 6. Primer arranque

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

La primera vez tarda varios minutos: descarga Maven, compila el backend y
empaqueta el frontend dentro del `jar`. Sigue el proceso con:

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f
```

Señales de que ha ido bien:

- `postgres`: `database system is ready to accept connections`
- `app`: Flyway aplica las migraciones y aparece `Tomcat started on port 8080`
- `caddy`: `certificate obtained successfully`

> El log de `app` puede mostrar un aviso de que no encuentra `DATABASE_URL`. Es
> **esperado**: esa variable es cosa de los PaaS; aquí la conexión llega por
> `SPRING_DATASOURCE_URL` y el aviso no afecta a nada.

Para no repetir `--env-file ... -f ...` en cada comando, deja un alias:

```bash
echo "alias dc='docker compose --env-file ~/dr-agramonte/.env.prod -f ~/dr-agramonte/docker-compose.prod.yml'" >> ~/.bashrc
source ~/.bashrc
```

---

## 7. Comprobar que está todo bien

```bash
curl -I https://www.dragramonte.com/
curl -s https://www.dragramonte.com/actuator/health
curl -I http://dragramonte.com/
curl -I http://www.dragramonte.com/
```

Esperado: `200` con certificado válido, `{"status":"UP"}`, y `301` hacia
`https://www.dragramonte.com/` en los dos últimos.

En el navegador: candado cerrado, la web carga, y una reserva de prueba en
`/reservar.html` se guarda sin errores 403 (eso confirma que el CORS está bien).

Entra al panel del médico con el `MEDICO_EMAIL` y el `MEDICO_PASSWORD` que
pusiste en `.env.prod`. **No uses las credenciales de demostración**
(`dr.agramonte@example.com` / `Medico123!`, publicadas en el README y en la
migración V3): al arrancar, `MedicoCuentaInicializador` reescribe esa cuenta con
las tuyas, de modo que en producción ya no sirven. Si por lo que sea entrases con
ellas, es que `MEDICO_PASSWORD` no llegó al contenedor — revísalo antes de abrir
la web a nadie.

---

## 8. Copias de seguridad

El volumen de Docker aguanta reinicios y actualizaciones, pero no un borrado por
error ni la pérdida del servidor. Con datos de pacientes de por medio, esto no es
opcional.

**Copia manual:**

```bash
cd ~/dr-agramonte && ./scripts/backup-db.sh
```

Deja `backups/dr_agramonte_AAAAMMDD_HHMMSS.sql.gz` y conserva las 14 últimas.

**Copia automática cada noche.** Con `crontab -e`, añade:

```cron
30 3 * * * cd /home/mar/dr-agramonte && ./scripts/backup-db.sh >> /home/mar/backup.log 2>&1
```

**Bájatelas a tu ordenador de vez en cuando.** Una copia que solo vive en el
servidor no te salva si pierdes el servidor:

```bash
scp mar@LA_IP:~/dr-agramonte/backups/*.sql.gz ./backups/
```

**Restaurar** un volcado:

```bash
gunzip -c backups/dr_agramonte_AAAAMMDD_HHMMSS.sql.gz | \
  docker compose --env-file .env.prod -f docker-compose.prod.yml \
  exec -T postgres psql -U agramonte -d dr_agramonte
```

---

## 9. Actualizar la aplicación

Desde tu PC haces `git push` como siempre. En el servidor:

```bash
cd ~/dr-agramonte && ./scripts/deploy.sh
```

El script saca una copia de la base de datos **antes** de nada (por si una
migración de Flyway sale mal), trae los cambios, reconstruye la imagen, levanta
los servicios, limpia imágenes viejas y comprueba la salud.

Hay unos segundos de corte mientras arranca el contenedor nuevo. Para una consulta
médica es asumible; si algún día molesta, se resuelve con dos réplicas y que Caddy
haga el relevo.

---

## 10. Mantenimiento

```bash
dc ps                      # estado de los tres servicios
dc logs -f app             # logs de la aplicación
dc restart app             # reiniciar solo la app
dc down                    # parar todo (los datos siguen en el volumen)
df -h                      # espacio en disco
docker system prune -af    # liberar espacio si el disco aprieta
```

Una vez al mes: `sudo apt update && sudo apt upgrade -y`, y reinicia si actualiza
el kernel. Los contenedores vuelven solos gracias a `restart: unless-stopped`.

Renovación del certificado: **no hay que hacer nada**, Caddy lo renueva cada 60
días por su cuenta. Los certificados viven en el volumen `caddy_data`; no lo
borres o habrá que pedirlos otra vez.

---

## 11. Si algo falla

| Síntoma | Causa habitual | Solución |
|---|---|---|
| Caddy no consigue el certificado | El DNS aún no apunta a la IP, o el 80/443 está cerrado | `dig +short www.dragramonte.com`, `sudo ufw status`; espera y `dc restart caddy` |
| `502 Bad Gateway` | La app no ha arrancado o se está cayendo | `dc logs app`. Casi siempre es la base de datos o una variable que falta |
| La app arranca y se muere sola | Sin memoria (OOM) en un VPS de 2 GB | Baja `mem_limit` de `app` a `768m` en `docker-compose.prod.yml` |
| `403` al reservar o al guardar | Origen no permitido en CORS | El dominio debe estar en `setAllowedOrigins` de `SecurityConfig.java` |
| Flyway falla al arrancar | Migración incompatible con los datos existentes | `dc logs app`, y restaura el último volcado antes de investigar |
| `password authentication failed` | Cambiaste `POSTGRES_PASSWORD` con el volumen ya creado | Vuelve a la contraseña original, o restaura un volcado en un volumen nuevo |

Validar el `Caddyfile` sin reiniciar nada:

```bash
dc exec caddy caddy validate --config /etc/caddy/Caddyfile
```

---

## 12. Qué no cubre esto

Lo que un alojamiento gestionado te daba hecho y aquí es tuyo:

- **Las copias de seguridad las haces tú** (apartado 8). Es el punto que más duele
  si se olvida.
- **Las actualizaciones del sistema operativo las haces tú** (apartado 10).
- **No hay alta disponibilidad.** Si el VPS cae, la web cae. Para una consulta
  privada es perfectamente razonable; si algún día hace falta más, un segundo
  servidor y un balanceador delante.
- **Vigila que sigue en pie.** Un aviso gratuito de <https://uptimerobot.com>
  apuntando a `https://www.dragramonte.com/actuator/health` te escribe si se cae,
  en vez de enterarte por un paciente.
