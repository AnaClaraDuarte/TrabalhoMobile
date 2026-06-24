# TrabalhoMobile

## Objetivo do app
Este projeto é um aplicativo Android simples em Kotlin que permite ao usuário:
- abrir a câmera pelo próprio app;
- capturar uma foto em tempo real;
- recuperar a localização atual no momento da captura;
- exibir a imagem capturada;
- converter a imagem para Base64;
- montar um JSON com Latitude, Longitude e Imagem;
- exibir esse JSON na tela;
- preparar uma requisição HTTP POST simulada para um endpoint fake.

## Tecnologias utilizadas
- Kotlin
- Jetpack Compose para a interface
- CameraX para captura de imagens
- FusedLocationProviderClient para obter latitude e longitude
- OkHttp para preparar a requisição HTTP POST

## Permissões usadas
O app solicita as seguintes permissões no AndroidManifest:
- CAMERA
- ACCESS_FINE_LOCATION
- ACCESS_COARSE_LOCATION
- INTERNET

## Fluxo do app
1. O app solicita permissões de câmera e localização.
2. O usuário toca em "Abrir Câmera".
3. A câmera abre em preview dentro do app.
4. O usuário toca em "Tirar Foto".
5. O app exibe a imagem capturada.
6. O app busca a localização atual.
7. O app converte a imagem para Base64.
8. O app monta um JSON com os dados da foto e da localização.
9. O app exibe o JSON na tela.
10. O app prepara uma requisição POST simulada.

## Exemplo do JSON gerado
```json
{
  "Latitude": -27.000000,
  "Longitude": -48.000000,
  "Imagem": "string_base64_da_imagem"
}
```

## Por que converter a imagem para Base64?
A imagem é convertida para Base64 porque o JSON é um formato de texto. Como a imagem é um conteúdo binário, ela precisa ser transformada em uma string para poder ser enviada dentro do JSON.

## Como rodar no Android Studio
1. Abra o Android Studio.
2. Selecione "Open" e escolha a pasta do projeto.
3. Aguarde a sincronização do Gradle.
4. Execute o app em um emulador ou dispositivo físico.
5. Conceda as permissões solicitadas para câmera e localização.

## Observação sobre o POST
O POST é apenas preparado e simulado para fins didáticos da atividade. O app registra o endpoint e o JSON no Log, sem fazer uma chamada real para a internet.

## Arquivos principais
- MainActivity.kt: tela principal, câmera, localização, JSON e POST simulado.
- AndroidManifest.xml: permissões do app.
- build.gradle: dependências do projeto.