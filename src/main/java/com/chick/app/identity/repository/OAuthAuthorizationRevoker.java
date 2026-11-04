package com.chick.app.identity.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Removes a user's stored OAuth2 authorizations so their refresh tokens stop working. */
@Repository
public class OAuthAuthorizationRevoker {

	private final JdbcTemplate jdbc;

	public OAuthAuthorizationRevoker(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public int revokeAll(String username) {
		return jdbc.update("delete from oauth2_authorization where principal_name = ?", username);
	}
}
