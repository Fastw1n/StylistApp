def test_get_empty_body_profile(test_client, auth_headers):
    response = test_client.get("/v1/profile/body", headers=auth_headers)

    assert response.status_code == 200
    assert response.json() == {
        "skin_tone": None,
        "eye_color": None,
        "hair_color": None,
        "height_cm": None,
        "weight_kg": None,
        "chest_cm": None,
        "waist_cm": None,
    }


def test_put_body_profile_success(test_client, auth_headers):
    response = test_client.put(
        "/v1/profile/body",
        headers=auth_headers,
        json={
            "skin_tone": "warm",
            "eye_color": "brown",
            "hair_color": "black",
            "height_cm": 171,
            "weight_kg": 62,
            "chest_cm": 88,
            "waist_cm": 70,
        },
    )

    assert response.status_code == 200
    assert response.json()["height_cm"] == 171
    assert response.json()["waist_cm"] == 70


def test_update_body_profile_success(test_client, auth_headers):
    test_client.put("/v1/profile/body", headers=auth_headers, json={"height_cm": 171})

    response = test_client.put(
        "/v1/profile/body",
        headers=auth_headers,
        json={"height_cm": 172, "weight_kg": 63},
    )

    assert response.status_code == 200
    assert response.json()["height_cm"] == 172
    assert response.json()["weight_kg"] == 63


def test_delete_body_profile_success(test_client, auth_headers):
    test_client.put("/v1/profile/body", headers=auth_headers, json={"height_cm": 171})

    delete_response = test_client.delete("/v1/profile/body", headers=auth_headers)
    get_response = test_client.get("/v1/profile/body", headers=auth_headers)

    assert delete_response.status_code == 200
    assert get_response.json()["height_cm"] is None


def test_body_profile_is_user_scoped(test_client, auth_headers, second_user_auth_headers):
    test_client.put("/v1/profile/body", headers=auth_headers, json={"height_cm": 180})

    response = test_client.get("/v1/profile/body", headers=second_user_auth_headers)

    assert response.status_code == 200
    assert response.json()["height_cm"] is None


def test_body_profile_requires_auth(test_client):
    response = test_client.get("/v1/profile/body")

    assert response.status_code == 401
