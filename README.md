# Da Roça Entregas

Aplicativo Android para agilizar a confirmação de entregas.

## iFood
Aceita links no formato `daroca://ifood/19840113`, abre a página de confirmação de entrega própria do iFood e tenta preencher automaticamente o número do pedido.

## 99Food
A estrutura de roteamento já aceita `daroca://99/...`, mas a integração será implementada depois que o fluxo/tela da 99Food for identificado e testado.

## APK pelo GitHub
O workflow em `.github/workflows/build-apk.yml` gera um APK de teste automaticamente no GitHub Actions.
