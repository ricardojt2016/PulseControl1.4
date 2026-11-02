# Pulse Control v1.4.1 — MuSE

Aplicación Android nativa Java, sin servicios web, cuentas ni telemetría.

## Funciones
- Emisor BLE Advertising MuSE (variante validada previamente para nivel bajo y STOP).
- Cuatro acciones directas: STOP, intensidad 1, 2 y 3. Los niveles 2/3 necesitan prueba física.
- Modo libre vertical: el dedo selecciona niveles discretos 1–3, al soltar se emite STOP.
- Patrones discretizados (0..3), frecuencia de ciclo aproximada ajustable 0,125 / 0,25 / 0,5 Hz.
- Dibujo de una curva de intensidad temporal propia (64 puntos), aplicable como patrón.
- Pantalla de búsqueda GATT separada, ordenada por RSSI; alias locales editables.
- Perfil MuSE local `diver1` no vinculado a una MAC.
- Batería MuSE: "No disponible". No se inventa porcentaje.
- STOP al salir de la actividad, al agotar el temporizador y al pulsar STOP. **La parada no se puede garantizar si Bluetooth falla, se apaga o se cierra el proceso. Conserva el mando físico.**

## Sobre el protocolo
MuSE puede recibir anuncios no dirigidos. Cualquier otro receptor compatible cercano podría reaccionar; no hay confirmación de recepción, emparejamiento, identidad ni telemetría de batería. La frecuencia ajustada corresponde a **ciclos de envolvente**, no a frecuencia mecánica del motor.

## Build
Repository ZIP contents must be uploaded to a *clean* GitHub repository root, preserving `.github/workflows/android.yml`. Use GitHub Actions > Pulse Control - Compilar APK; artifact `PulseControl-v1.4.1-APK`.

Android build requires Android SDK and Gradle in the GitHub runner; the full Android build has not been executed inside this local environment. Java-only tests for PatternMath can be run via `javac`.

## Licencias
El código de Pulse Control se ha escrito para este proyecto. Consultadas como referencias públicas las implementaciones MuSE de LS-Intiface-Bridge (MIT) y LVS-Gateway; no se incorpora su código fuente.
