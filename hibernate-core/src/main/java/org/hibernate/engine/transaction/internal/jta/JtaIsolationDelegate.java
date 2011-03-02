/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2011, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.engine.transaction.internal.jta;

import java.sql.Connection;
import java.sql.SQLException;
import javax.transaction.NotSupportedException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.hibernate.HibernateException;
import org.hibernate.engine.jdbc.spi.SQLExceptionHelper;
import org.hibernate.engine.transaction.spi.IsolationDelegate;
import org.hibernate.engine.transaction.spi.TransactionCoordinator;
import org.hibernate.jdbc.Work;
import org.hibernate.service.jdbc.connections.spi.ConnectionProvider;

/**
 * An isolation delegate for JTA environments.
 *
 * @author Steve Ebersole
 */
public class JtaIsolationDelegate implements IsolationDelegate {
	private static final Logger log = LoggerFactory.getLogger( JtaIsolationDelegate.class );

	private final TransactionCoordinator transactionCoordinator;

	public JtaIsolationDelegate(TransactionCoordinator transactionCoordinator) {
		this.transactionCoordinator = transactionCoordinator;
	}

	protected TransactionManager transactionManager() {
		return transactionCoordinator.getTransactionContext()
				.getTransactionEnvironment()
				.getJtaPlatform()
				.retrieveTransactionManager();
	}

	protected ConnectionProvider connectionProvider() {
		return transactionCoordinator.getTransactionContext()
				.getTransactionEnvironment()
				.getJdbcServices()
				.getConnectionProvider();
	}

	protected SQLExceptionHelper sqlExceptionHelper() {
		return transactionCoordinator.getTransactionContext()
				.getTransactionEnvironment()
				.getJdbcServices()
				.getSqlExceptionHelper();
	}

	@Override
	public void delegateWork(Work work, boolean transacted) throws HibernateException {
		TransactionManager transactionManager = transactionManager();

		try {
			// First we suspend any current JTA transaction
			Transaction surroundingTransaction = transactionManager.suspend();
			if ( log.isDebugEnabled() ) {
				log.debug( "surrounding JTA transaction suspended [" + surroundingTransaction + "]" );
			}

			boolean hadProblems = false;
			try {
				// then perform the requested work
				if ( transacted ) {
					doTheWorkInNewTransaction( work, transactionManager );
				}
				else {
					doTheWorkInNoTransaction( work );
				}
			}
			catch ( HibernateException e ) {
				hadProblems = true;
				throw e;
			}
			finally {
				try {
					transactionManager.resume( surroundingTransaction );
					if ( log.isDebugEnabled() ) {
						log.debug( "surrounding JTA transaction resumed [" + surroundingTransaction + "]" );
					}
				}
				catch( Throwable t ) {
					// if the actually work had an error use that, otherwise error based on t
					if ( !hadProblems ) {
						//noinspection ThrowFromFinallyBlock
						throw new HibernateException( "Unable to resume previously suspended transaction", t );
					}
				}
			}
		}
		catch ( SystemException e ) {
			throw new HibernateException( "Unable to suspend current JTA transaction", e );
		}
	}

	private void doTheWorkInNewTransaction(Work work, TransactionManager transactionManager) {
		try {
			// start the new isolated transaction
			transactionManager.begin();

			try {
				doTheWork( work );
				// if everythign went ok, commit the isolated transaction
				transactionManager.commit();
			}
			catch ( Exception e ) {
				try {
					transactionManager.rollback();
				}
				catch ( Exception ignore ) {
					log.info( "Unable to rollback isolated transaction on error [" + e + "] : [" + ignore + "]" );
				}
			}
		}
		catch ( SystemException e ) {
			throw new HibernateException( "Unable to start isolated transaction", e );
		}
		catch ( NotSupportedException e ) {
			throw new HibernateException( "Unable to start isolated transaction", e );
		}
	}

	private void doTheWorkInNoTransaction(Work work) {
		doTheWork( work );
	}

	private void doTheWork(Work work) {
		try {
			// obtain our isolated connection
			Connection connection = connectionProvider().getConnection();
			try {
				// do the actual work
				work.execute( connection );
			}
			catch ( HibernateException e ) {
				throw e;
			}
			catch ( Exception e ) {
				throw new HibernateException( "Unable to perform isolated work", e );
			}
			finally {
				try {
					// no matter what, release the connection (handle)
					connectionProvider().closeConnection( connection );
				}
				catch ( Throwable ignore ) {
					log.info( "Unable to release isolated connection [" + ignore + "]" );
				}
			}
		}
		catch ( SQLException sqle ) {
			throw sqlExceptionHelper().convert( sqle, "unable to obtain isolated JDBC connection" );
		}
	}

}

