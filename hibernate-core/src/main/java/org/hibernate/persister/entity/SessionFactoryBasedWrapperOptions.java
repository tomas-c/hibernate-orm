/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.persister.entity;

import java.util.TimeZone;

import org.hibernate.Internal;
import org.hibernate.engine.jdbc.LobCreator;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.descriptor.WrapperOptions;

/**
 *
 * @author Christian Beikov
 */
@Internal
public class SessionFactoryBasedWrapperOptions implements WrapperOptions {

	private final SessionFactoryImplementor factory;

	public SessionFactoryBasedWrapperOptions(SessionFactoryImplementor factory) {
		this.factory = factory;
	}

	@Override
	public SharedSessionContractImplementor getSession() {
		throw new UnsupportedOperationException();
	}

	@Override
	public SessionFactoryImplementor getSessionFactory() {
		return factory;
	}

	@Override
	public boolean useStreamForLobBinding() {
		return factory.getFastSessionServices().useStreamForLobBinding();
	}

	@Override
	public int getPreferredSqlTypeCodeForBoolean() {
		return factory.getFastSessionServices().getPreferredSqlTypeCodeForBoolean();
	}

	@Override
	public LobCreator getLobCreator() {
		return factory.getJdbcServices().getLobCreator( getSession() );
	}

	@Override
	public TimeZone getJdbcTimeZone() {
		return factory.getSessionFactoryOptions().getJdbcTimeZone();
	}
}
