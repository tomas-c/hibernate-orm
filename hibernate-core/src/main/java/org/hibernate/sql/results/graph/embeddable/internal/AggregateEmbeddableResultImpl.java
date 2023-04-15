/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.results.graph.embeddable.internal;

import org.hibernate.metamodel.mapping.EmbeddableMappingType;
import org.hibernate.metamodel.mapping.EmbeddableValuedModelPart;
import org.hibernate.metamodel.mapping.SelectableMapping;
import org.hibernate.spi.NavigablePath;
import org.hibernate.sql.ast.SqlAstJoinType;
import org.hibernate.sql.ast.spi.FromClauseAccess;
import org.hibernate.sql.ast.spi.SqlAstCreationState;
import org.hibernate.sql.ast.spi.SqlExpressionResolver;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableGroupJoin;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.results.graph.AbstractFetchParent;
import org.hibernate.sql.results.graph.AssemblerCreationState;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.DomainResultAssembler;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.Fetch;
import org.hibernate.sql.results.graph.FetchParent;
import org.hibernate.sql.results.graph.FetchParentAccess;
import org.hibernate.sql.results.graph.embeddable.EmbeddableInitializer;
import org.hibernate.sql.results.graph.embeddable.EmbeddableResult;
import org.hibernate.sql.results.graph.embeddable.EmbeddableResultGraphNode;
import org.hibernate.sql.results.graph.internal.ImmutableFetchList;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * A Result for an embeddable that is mapped as aggregate e.g. STRUCT, JSON or XML.
 * This is only used when {@link EmbeddableMappingType#shouldSelectAggregateMapping()} returns <code>true</code>.
 * The main difference is that it selects only the aggregate column and
 * uses {@link org.hibernate.sql.results.graph.DomainResultCreationState#visitNestedFetches(FetchParent)}
 * for creating the fetches for the attributes of the embeddable.
 */
public class AggregateEmbeddableResultImpl<T> extends AbstractFetchParent implements EmbeddableResultGraphNode,
		DomainResult<T>,
		EmbeddableResult<T> {
	private final String resultVariable;
	private final boolean containsAnyNonScalars;
	private final NavigablePath initializerNavigablePath;
	private final SqlSelection aggregateSelection;

	public AggregateEmbeddableResultImpl(
			NavigablePath navigablePath,
			EmbeddableValuedModelPart embeddedPartDescriptor,
			String resultVariable,
			DomainResultCreationState creationState) {
		super( embeddedPartDescriptor.getEmbeddableTypeDescriptor(), navigablePath );
		this.resultVariable = resultVariable;
		/*
			An `{embeddable_result}` sub-path is created for the corresponding initializer to differentiate it from a fetch-initializer if this embedded is also fetched.
			The Jakarta Persistence spec says that any embedded value selected in the result should not be part of the state of any managed entity.
			Using this `{embeddable_result}` sub-path avoids this situation.
		*/
		this.initializerNavigablePath = navigablePath.append( "{embeddable_result}" );

		final SqlAstCreationState sqlAstCreationState = creationState.getSqlAstCreationState();
		final FromClauseAccess fromClauseAccess = sqlAstCreationState.getFromClauseAccess();

		final TableGroup tableGroup = fromClauseAccess.resolveTableGroup(
				navigablePath,
				np -> {
					final EmbeddableValuedModelPart embeddedValueMapping = embeddedPartDescriptor.getEmbeddableTypeDescriptor()
							.getEmbeddedValueMapping();
					final TableGroup tg = fromClauseAccess.findTableGroup( navigablePath.getParent() );
					final TableGroupJoin tableGroupJoin = embeddedValueMapping.createTableGroupJoin(
							navigablePath,
							tg,
							resultVariable,
							null,
							SqlAstJoinType.INNER,
							true,
							false,
							sqlAstCreationState
					);
					tg.addTableGroupJoin( tableGroupJoin );
					return tableGroupJoin.getJoinedGroup();
				}
		);

		final SqlExpressionResolver sqlExpressionResolver = sqlAstCreationState.getSqlExpressionResolver();
		final TableReference tableReference = tableGroup.getPrimaryTableReference();
		final SelectableMapping selectableMapping = embeddedPartDescriptor.getEmbeddableTypeDescriptor().getAggregateMapping();
		final Expression expression = sqlExpressionResolver.resolveSqlExpression( tableReference, selectableMapping );
		final TypeConfiguration typeConfiguration = sqlAstCreationState.getCreationContext()
				.getSessionFactory()
				.getTypeConfiguration();
		this.aggregateSelection = sqlExpressionResolver.resolveSqlSelection(
				expression,
				// Using the Object[] type here, so that a different JDBC extractor is chosen
				typeConfiguration.getJavaTypeRegistry().resolveDescriptor( Object[].class ),
				null,
				typeConfiguration
		);
		resetFetches( creationState.visitNestedFetches( this ) );
		this.containsAnyNonScalars = determineIfContainedAnyScalars( getFetches() );
	}

	private static boolean determineIfContainedAnyScalars(ImmutableFetchList fetches) {
		for ( Fetch fetch : fetches ) {
			if ( fetch.containsAnyNonScalarResults() ) {
				return true;
			}
		}

		return false;
	}

	@Override
	public String getResultVariable() {
		return resultVariable;
	}

	@Override
	public boolean containsAnyNonScalarResults() {
		return containsAnyNonScalars;
	}

	@Override
	public EmbeddableMappingType getFetchContainer() {
		return (EmbeddableMappingType) super.getFetchContainer();
	}

	@Override
	public JavaType<?> getResultJavaType() {
		return getReferencedMappingType().getJavaType();
	}

	@Override
	public EmbeddableMappingType getReferencedMappingType() {
		return getFetchContainer();
	}

	@Override
	public EmbeddableValuedModelPart getReferencedMappingContainer() {
		return getFetchContainer().getEmbeddedValueMapping();
	}

	@Override
	public DomainResultAssembler<T> createResultAssembler(
			FetchParentAccess parentAccess,
			AssemblerCreationState creationState) {
		final EmbeddableInitializer initializer = creationState.resolveInitializer(
				initializerNavigablePath,
				getReferencedModePart(),
				() -> new AggregateEmbeddableResultInitializer(
						parentAccess,
						this,
						creationState,
						aggregateSelection
				)
		).asEmbeddableInitializer();

		assert initializer != null;

		return new EmbeddableAssembler( initializer );
	}
}
