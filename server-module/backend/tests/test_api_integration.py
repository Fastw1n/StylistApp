def test_register_endpoint_success(test_client):
    response = test_client.post(
        "/v1/auth/register",
        json={"email": " New.User@Example.com ", "password": "secret123", "name": "New User"},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["token"]
    assert payload["user"]["email"] == "new.user@example.com"
    assert payload["user"]["name"] == "New User"


def test_register_endpoint_duplicate_email_returns_409(test_client):
    body = {"email": "duplicate@example.com", "password": "secret123"}

    first = test_client.post("/v1/auth/register", json=body)
    second = test_client.post("/v1/auth/register", json=body)

    assert first.status_code == 200
    assert second.status_code == 409


def test_register_endpoint_invalid_email_returns_400(test_client):
    response = test_client.post(
        "/v1/auth/register",
        json={"email": "not-an-email", "password": "secret123"},
    )

    assert response.status_code == 400


def test_login_endpoint_success(test_client):
    test_client.post(
        "/v1/auth/register",
        json={"email": "login@example.com", "password": "secret123"},
    )

    response = test_client.post(
        "/v1/auth/login",
        json={"email": "LOGIN@example.com", "password": "secret123"},
    )

    assert response.status_code == 200
    assert response.json()["user"]["email"] == "login@example.com"


def test_login_endpoint_wrong_password_returns_401(test_client):
    test_client.post(
        "/v1/auth/register",
        json={"email": "wrong-password@example.com", "password": "secret123"},
    )

    response = test_client.post(
        "/v1/auth/login",
        json={"email": "wrong-password@example.com", "password": "badpass"},
    )

    assert response.status_code == 401


def test_login_endpoint_unknown_user_returns_401(test_client):
    response = test_client.post(
        "/v1/auth/login",
        json={"email": "missing@example.com", "password": "secret123"},
    )

    assert response.status_code == 401


def test_me_endpoint_with_token_returns_current_user(test_client):
    auth = test_client.post(
        "/v1/auth/register",
        json={"email": "me@example.com", "password": "secret123", "name": "Me"},
    ).json()

    response = test_client.get("/v1/auth/me", headers={"Authorization": f"Bearer {auth['token']}"})

    assert response.status_code == 200
    assert response.json()["email"] == "me@example.com"


def test_me_endpoint_without_token_returns_401(test_client):
    response = test_client.get("/v1/auth/me")

    assert response.status_code == 401


def test_me_endpoint_with_invalid_token_returns_401(test_client):
    response = test_client.get("/v1/auth/me", headers={"Authorization": "Bearer not-a-uuid"})

    assert response.status_code == 401


def test_unknown_route_returns_404(test_client):
    response = test_client.get("/v1/does-not-exist")

    assert response.status_code == 404
