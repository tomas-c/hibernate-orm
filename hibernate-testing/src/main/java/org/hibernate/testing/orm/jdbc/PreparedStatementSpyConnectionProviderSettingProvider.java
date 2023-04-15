/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.testing.orm.jdbc;

import org.hibernate.testing.orm.junit.SettingProvider;

/**
 * @author Steve Ebersole
 */
public class PreparedStatementSpyConnectionProviderSettingProvider implements SettingProvider.Provider<PreparedStatementSpyConnectionProvider> {
	@Override
	public PreparedStatementSpyConnectionProvider getSetting() {
		return new PreparedStatementSpyConnectionProvider();
	}
}
