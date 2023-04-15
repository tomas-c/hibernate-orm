/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.sql.internal;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.hibernate.metamodel.MappingMetamodel;
import org.hibernate.metamodel.mapping.BasicValuedModelPart;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.ManagedMappingType;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.metamodel.mapping.ModelPartContainer;
import org.hibernate.metamodel.model.domain.EntityDomainType;
import org.hibernate.query.sqm.tree.from.SqmFrom;
import org.hibernate.spi.NavigablePath;
import org.hibernate.query.SemanticException;
import org.hibernate.query.sqm.SemanticQueryWalker;
import org.hibernate.query.sqm.StrictJpaComplianceViolation;
import org.hibernate.query.sqm.tree.domain.SqmBasicValuedSimplePath;
import org.hibernate.query.sqm.tree.domain.SqmTreatedPath;
import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.SqlAstWalker;
import org.hibernate.sql.ast.spi.FromClauseAccess;
import org.hibernate.sql.ast.spi.SqlAstCreationState;
import org.hibernate.sql.ast.tree.expression.ColumnReference;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.SqlSelectionExpression;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.ast.tree.update.Assignable;

/**
 * @author Steve Ebersole
 */
public class BasicValuedPathInterpretation<T> extends AbstractSqmPathInterpretation<T> implements Assignable,  DomainResultProducer<T> {
	/**
	 * Static factory
	 */
	public static <T> BasicValuedPathInterpretation<T> from(
			SqmBasicValuedSimplePath<T> sqmPath,
			SqlAstCreationState sqlAstCreationState,
			SemanticQueryWalker sqmWalker,
			boolean jpaQueryComplianceEnabled,
			Clause currentClause) {
		final FromClauseAccess fromClauseAccess = sqlAstCreationState.getFromClauseAccess();
		final TableGroup tableGroup = fromClauseAccess.getTableGroup( sqmPath.getNavigablePath().getParent() );

		EntityMappingType treatTarget = null;
		if ( jpaQueryComplianceEnabled ) {
			if ( sqmPath.getLhs() instanceof SqmTreatedPath ) {
				final EntityDomainType treatTargetDomainType = ( (SqmTreatedPath) sqmPath.getLhs() ).getTreatTarget();

				final MappingMetamodel mappingMetamodel = sqlAstCreationState.getCreationContext()
						.getSessionFactory()
						.getRuntimeMetamodels()
						.getMappingMetamodel();
				treatTarget = mappingMetamodel.findEntityDescriptor( treatTargetDomainType.getHibernateEntityName() );
			}
			else if ( sqmPath.getLhs().getNodeType() instanceof EntityDomainType ) {
				final EntityDomainType entityDomainType = (EntityDomainType) sqmPath.getLhs().getNodeType();
				final MappingMetamodel mappingMetamodel = sqlAstCreationState.getCreationContext()
						.getSessionFactory()
						.getRuntimeMetamodels()
						.getMappingMetamodel();
				treatTarget = mappingMetamodel.findEntityDescriptor( entityDomainType.getHibernateEntityName() );
			}
		}

		final ModelPartContainer modelPart = tableGroup.getModelPart();
		final BasicValuedModelPart mapping;
		// In the select, group by, order by and having clause we have to make sure we render the column of the target table,
		// never the FK column, if the lhs is a SqmFrom i.e. something explicitly queried/joined.
		if ( ( currentClause == Clause.GROUP || currentClause == Clause.SELECT || currentClause == Clause.ORDER || currentClause == Clause.HAVING )
				&& sqmPath.getLhs() instanceof SqmFrom<?, ?>
				&& modelPart.getPartMappingType() instanceof ManagedMappingType ) {
			mapping = (BasicValuedModelPart) ( (ManagedMappingType) modelPart.getPartMappingType() ).findSubPart(
					sqmPath.getReferencedPathSource().getPathName(),
					treatTarget
			);
		}
		else {
			mapping = (BasicValuedModelPart) modelPart.findSubPart(
					sqmPath.getReferencedPathSource().getPathName(),
					treatTarget
			);
		}

		if ( mapping == null ) {
			if ( jpaQueryComplianceEnabled ) {
				// to get the better error, see if we got nothing because of treat handling
				final ModelPart subPart = tableGroup.getModelPart().findSubPart(
						sqmPath.getReferencedPathSource().getPathName(),
						null
				);
				if ( subPart != null ) {
					throw new StrictJpaComplianceViolation( StrictJpaComplianceViolation.Type.IMPLICIT_TREAT );
				}
			}

			throw new SemanticException( "`" + sqmPath.getNavigablePath() + "` did not reference a known model part" );
		}

		final TableReference tableReference = tableGroup.resolveTableReference(
				sqmPath.getNavigablePath(),
				mapping.getContainingTableExpression()
		);

		final Expression expression = sqlAstCreationState.getSqlExpressionResolver().resolveSqlExpression(
				tableReference,
				mapping
		);

		final ColumnReference columnReference;
		if ( expression instanceof ColumnReference ) {
			columnReference = ( (ColumnReference) expression );
		}
		else if ( expression instanceof SqlSelectionExpression ) {
			final Expression selectedExpression = ( (SqlSelectionExpression) expression ).getSelection().getExpression();
			assert selectedExpression instanceof ColumnReference;
			columnReference = (ColumnReference) selectedExpression;
		}
		else {
			throw new UnsupportedOperationException( "Unsupported basic-valued path expression : " + expression );
		}

		return new BasicValuedPathInterpretation<>( columnReference, sqmPath.getNavigablePath(), mapping, tableGroup );
	}

	private final ColumnReference columnReference;

	public BasicValuedPathInterpretation(
			ColumnReference columnReference,
			NavigablePath navigablePath,
			BasicValuedModelPart mapping,
			TableGroup tableGroup) {
		super( navigablePath, mapping, tableGroup );
		assert columnReference != null;
		this.columnReference = columnReference;
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// DomainResultProducer

	@Override
	public Expression getSqlExpression() {
		return columnReference;
	}

	@Override
	public void accept(SqlAstWalker sqlTreeWalker) {
		columnReference.accept( sqlTreeWalker );
	}

	@Override
	public String toString() {
		return "BasicValuedPathInterpretation(" + getNavigablePath() + ")";
	}

	@Override
	public void visitColumnReferences(Consumer<ColumnReference> columnReferenceConsumer) {
		columnReferenceConsumer.accept( columnReference );
	}

	@Override
	public List<ColumnReference> getColumnReferences() {
		return Collections.singletonList( columnReference );
	}
}
