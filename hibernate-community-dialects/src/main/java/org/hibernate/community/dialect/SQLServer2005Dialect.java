/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.community.dialect;

import org.hibernate.dialect.DatabaseVersion;

/**
 * A dialect for Microsoft SQL Server 2005.
 *
 * @author Yoryos Valotasios
 * @author Lukasz Antoniak
 *
 * @deprecated use {@code SQLServerDialect(9)}
 */
@Deprecated
public class SQLServer2005Dialect extends SQLServerLegacyDialect {

	public SQLServer2005Dialect() {
		super( DatabaseVersion.make( 9 ) );
	}

}
