"""Compara dos carpetas de capturas hechas con visual/capturar.mjs.

    python3 visual/comparar.py <antes> <despues>

Cuenta un píxel como distinto si algún canal cambia en más de 8 niveles, para
no saltar por el suavizado del texto. Sale con código 1 si hay diferencias.
"""
import os
import sys

from PIL import Image, ImageChops

UMBRAL = 8


def pixeles_distintos(mascara):
    histograma = mascara.histogram()
    return histograma[255]


def main(antes, despues):
    distintas = 0
    nombres = sorted(os.listdir(antes))
    for nombre in nombres:
        a = Image.open(os.path.join(antes, nombre)).convert('RGB')
        ruta_b = os.path.join(despues, nombre)
        if not os.path.exists(ruta_b):
            print('FALTA  ', nombre)
            distintas += 1
            continue
        b = Image.open(ruta_b).convert('RGB')
        if a.size != b.size:
            print('TAMAÑO ', nombre, a.size, '->', b.size)
            distintas += 1
            continue
        mascara = ImageChops.difference(a, b).convert('L').point(lambda v: 255 if v > UMBRAL else 0)
        caja = mascara.getbbox()
        if caja:
            print('DISTINTA', nombre, pixeles_distintos(mascara), 'px en', caja)
            distintas += 1
    print(f'{len(nombres)} escenas, {distintas} con diferencias')
    return 1 if distintas else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1], sys.argv[2]))
