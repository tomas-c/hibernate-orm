/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.ast.spi;

import org.hibernate.LockMode;
import org.hibernate.engine.spi.LoadQueryInfluencers;

/**
 * Access to stuff used while creating a SQL AST
 *
 * @author Steve Ebersole
 */
public interface SqlAstCreationState {
	SqlAstCreationContext getCreationContext();

	SqlAstProcessingState getCurrentProcessingState();

	SqlExpressionResolver getSqlExpressionResolver();

	FromClauseAccess getFromClauseAccess();

	SqlAliasBaseGenerator getSqlAliasBaseGenerator();

	LoadQueryInfluencers getLoadQueryInfluencers();

	void registerLockMode(String identificationVariable, LockMode explicitLockMode);
}
