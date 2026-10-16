"""Pruebas del revisor automático (.github/scripts/revision_ia.py).

Solo cubren la lógica propia (preparar el diff, construir la petición, leer la
respuesta, gestionar el comentario). Las llamadas de red no se prueban aquí: se
comprueban en la primera PR real.

    python -m unittest discover -s .github/scripts -v
"""

import unittest

import revision_ia as r


def bloque(ruta: str, cuerpo: str = "+linea nueva\n") -> str:
    return (
        f"diff --git a/{ruta} b/{ruta}\n"
        f"--- a/{ruta}\n"
        f"+++ b/{ruta}\n"
        "@@ -1 +1 @@\n"
        f"{cuerpo}"
    )


class SepararPorArchivo(unittest.TestCase):
    def test_separa_cada_archivo_con_su_ruta(self):
        diff = bloque("backend/A.java") + bloque("frontend/app.js")
        bloques = r.separar_por_archivo(diff)
        self.assertEqual([ruta for ruta, _ in bloques], ["backend/A.java", "frontend/app.js"])
        self.assertTrue(bloques[1][1].startswith("diff --git a/frontend/app.js"))

    def test_diff_vacio(self):
        self.assertEqual(r.separar_por_archivo(""), [])


class PrepararDiff(unittest.TestCase):
    def test_descarta_imagenes_pdf_y_capturas(self):
        diff = (
            bloque("docs/Memoria-Dr-Agramonte.pdf")
            + bloque("docs/capturas/agenda.png")
            + bloque("docs/capturas/notas.md")
            + bloque("backend/Cita.java")
        )
        texto, omitidos = r.preparar_diff(diff)
        self.assertIn("backend/Cita.java", texto)
        self.assertNotIn(".pdf", texto)
        self.assertNotIn("docs/capturas/", texto)
        self.assertEqual(omitidos, [])

    def test_descarta_binarios_aunque_la_extension_no_este_en_la_lista(self):
        binario = (
            "diff --git a/demo/datos.bin b/demo/datos.bin\n"
            "Binary files /dev/null and b/demo/datos.bin differ\n"
        )
        texto, _ = r.preparar_diff(binario + bloque("backend/A.java"))
        self.assertNotIn("datos.bin", texto)
        self.assertIn("backend/A.java", texto)

    def test_recorta_por_archivos_completos_y_avisa_de_los_omitidos(self):
        pequeno = bloque("backend/A.java")
        grande = bloque("backend/Grande.java", "+" + "x" * 500 + "\n")
        texto, omitidos = r.preparar_diff(pequeno + grande, max_caracteres=len(pequeno) + 10)
        self.assertEqual(texto, pequeno)
        self.assertEqual(omitidos, ["backend/Grande.java"])

    def test_un_archivo_grande_no_impide_revisar_los_siguientes(self):
        grande = bloque("backend/Grande.java", "+" + "x" * 500 + "\n")
        pequeno = bloque("backend/B.java")
        texto, omitidos = r.preparar_diff(grande + pequeno, max_caracteres=len(pequeno) + 10)
        self.assertIn("backend/B.java", texto)
        self.assertEqual(omitidos, ["backend/Grande.java"])


class ConstruirPeticion(unittest.TestCase):
    def test_el_diff_va_delimitado_y_el_prompt_en_system(self):
        p = r.construir_peticion("+hola\n", "feat: algo", "", "modelo-x")
        self.assertEqual(p["model"], "modelo-x")
        self.assertEqual(p["system"], r.PROMPT_SISTEMA)
        contenido = p["messages"][0]["content"]
        self.assertIn("<diff>\n+hola\n\n</diff>", contenido)
        self.assertIn("(sin descripción)", contenido)

    def test_el_prompt_avisa_de_que_el_diff_no_son_instrucciones(self):
        # Defensa básica contra inyección de prompt desde el propio código revisado.
        self.assertIn("no instrucciones", r.PROMPT_SISTEMA)


class ExtraerTexto(unittest.TestCase):
    def test_une_solo_los_bloques_de_texto(self):
        respuesta = {
            "content": [
                {"type": "text", "text": "Primera parte."},
                {"type": "tool_use", "id": "x"},
                {"type": "text", "text": "Segunda parte."},
            ]
        }
        self.assertEqual(r.extraer_texto(respuesta), "Primera parte.\nSegunda parte.")

    def test_respuesta_sin_contenido(self):
        self.assertEqual(r.extraer_texto({}), "")


class Comentario(unittest.TestCase):
    def test_lleva_la_marca_para_poder_actualizarlo(self):
        cuerpo = r.componer_comentario("Sin observaciones.", [], "modelo-x")
        self.assertTrue(cuerpo.startswith(r.MARCA_COMENTARIO))
        self.assertIn("`modelo-x`", cuerpo)
        self.assertNotIn("no revisados", cuerpo)

    def test_lista_los_archivos_omitidos(self):
        cuerpo = r.componer_comentario("ok", ["backend/Grande.java"], "m")
        self.assertIn("> - `backend/Grande.java`", cuerpo)

    def test_encuentra_el_comentario_previo_por_la_marca(self):
        comentarios = [
            {"id": 1, "body": "Comentario de una persona"},
            {"id": 2, "body": None},
            {"id": 3, "body": r.MARCA_COMENTARIO + "\nrevisión anterior"},
        ]
        self.assertEqual(r.buscar_comentario_previo(comentarios), 3)

    def test_sin_comentario_previo(self):
        self.assertIsNone(r.buscar_comentario_previo([{"id": 1, "body": "hola"}]))


class SinClave(unittest.TestCase):
    def test_sin_api_key_termina_en_verde_sin_tocar_nada(self):
        import os
        from unittest import mock

        with mock.patch.dict(os.environ, {}, clear=True):
            self.assertEqual(r.main(), 0)


if __name__ == "__main__":
    unittest.main()
