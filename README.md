# StylistApp

StylistApp - дипломный проект для цифрового гардероба. В проект входят Android-приложение, backend API, сервис обработки изображений, PostgreSQL, SeaweedFS и nginx.

## Что умеет проект

- регистрация и авторизация пользователей;
- добавление вещей по фотографии;
- ML-сегментация одежды через отдельный image-processing service;
- хранение оригинальных и нормализованных изображений в SeaweedFS;
- просмотр гардероба;
- избранное и переименование вещей;
- создание и просмотр образов;
- параметры фигуры пользователя;
- изоляция данных между пользователями;
- backend-тесты с coverage выше 80%.

## Структура репозитория

```text
android app/app1/              Android-приложение
server-module/backend/         FastAPI backend
server-module/docker-compose.yml
server-module/nginx/           nginx reverse proxy config
image-processing-module/       ML/image segmentation service
image-processing-module/models/ локальная папка для весов модели, не хранится в git
load-tests/                    k6 нагрузочные тесты
TESTING.md                     инструкция по тестам и coverage
```

## Требования

- Docker Desktop или Docker Engine с Docker Compose;
- Git;
- Android Studio для запуска Android-приложения;
- файл весов YOLO-модели `best.pt`.

Веса модели не коммитятся в репозиторий. Ссылку на скачивание весов нужно указать самостоятельно, например в тексте работы, README-форке или отдельной инструкции. В этом README намеренно нет конкретной ссылки.

## Подготовка весов модели

Создайте папку:

```bash
mkdir -p image-processing-module/models
```

Положите файл весов сюда:

```text
image-processing-module/models/best.pt
```

По умолчанию Docker Compose монтирует эту папку в контейнер image-processing как `/app/models`, а сервис читает путь:

```text
/app/models/best.pt
```

Если нужен другой путь внутри контейнера, измените переменную `YOLO_WEIGHTS_PATH` в `server-module/docker-compose.yml`.

## Запуск backend через Docker Compose

Перейдите в папку серверного модуля:

```bash
cd server-module
```

Соберите и запустите все сервисы:

```bash
docker compose up --build
```

В составе compose поднимаются:

- `stylist_db` - PostgreSQL 16;
- `stylist_seaweed_master` - SeaweedFS master;
- `stylist_seaweed_volume` - SeaweedFS volume server;
- `stylist_seaweed_filer` - SeaweedFS filer;
- `stylist_image_processing` - FastAPI ML service на порту `8001`;
- `stylist_api` - FastAPI backend;
- `stylist_nginx` - reverse proxy на порту `8000`.

После запуска backend доступен по адресу:

```text
http://localhost:8000
```

Android-эмулятор обращается к этому же серверу через:

```text
http://10.0.2.2:8000/
```

Проверка API:

```bash
curl http://localhost:8000/openapi.json
```

Для защищенных endpoints нужен Bearer token, который возвращают:

```text
POST /v1/auth/register
POST /v1/auth/login
```

## Основные переменные окружения

Переменные задаются в `server-module/docker-compose.yml`.

Backend:

```text
DATABASE_URL=postgresql+psycopg2://stylist:stylist@db:5432/stylist
BASE_URL=http://localhost:8000
STORAGE_BACKEND=seaweedfs
SEAWEEDFS_FILER_URL=http://seaweed-filer:8888
SEAWEEDFS_COLLECTION=stylist-media
ML_SERVICE_URL=http://image-processing:8001
```

Image-processing:

```text
YOLO_WEIGHTS_PATH=/app/models/best.pt
```

## nginx и media

nginx слушает внешний порт `8000` и проксирует:

- `/v1/...` в backend API;
- `/media/...` в SeaweedFS collection `stylist-media`;
- `/seaweedfsstatic/...` в SeaweedFS static assets.

Это позволяет Android-приложению получать API и изображения через один адрес `http://10.0.2.2:8000/`.

## Остановка

```bash
cd server-module
docker compose down
```

Остановить и удалить volumes с данными:

```bash
docker compose down -v
```

## Запуск Android-приложения

Откройте проект:

```text
android app/app1
```

в Android Studio.

Перед запуском убедитесь, что backend поднят:

```text
http://localhost:8000
```

В эмуляторе Android backend должен быть доступен как:

```text
http://10.0.2.2:8000/
```

## Тесты

Подробная инструкция находится в [TESTING.md](TESTING.md).

Backend coverage:

```bash
cd server-module/backend
pytest --cov=app --cov-report=term-missing --cov-report=html --cov-fail-under=80
```

Проверенный результат:

```text
119 passed
Total backend line coverage: 94.62%
```

Android unit tests:

```powershell
cd "android app/app1"
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat testDebugUnitTest
```

Image-processing contract tests:

```bash
cd image-processing-module
pytest
```

Load tests:

```bash
k6 run load-tests/stylist-load-test.js
```

## Частые проблемы

### `ML service failed` или ошибка про `best.pt`

Проверьте, что файл существует:

```text
image-processing-module/models/best.pt
```

и что compose запущен из папки `server-module`.

### Android не видит backend

Проверьте, что nginx слушает порт `8000`:

```bash
docker compose ps
```

В Android-эмуляторе используйте не `localhost`, а:

```text
http://10.0.2.2:8000/
```

### Нужно полностью очистить данные

```bash
cd server-module
docker compose down -v
docker compose up --build
```
