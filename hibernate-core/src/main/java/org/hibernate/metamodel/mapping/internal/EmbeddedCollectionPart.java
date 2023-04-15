/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.mapping.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.hibernate.engine.FetchStyle;
import org.hibernate.engine.FetchTiming;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.metamodel.mapping.CollectionPart;
import org.hibernate.metamodel.mapping.EmbeddableMappingType;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.MappingType;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.metamodel.mapping.SelectableConsumer;
import org.hibernate.metamodel.model.domain.NavigableRole;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.property.access.internal.PropertyAccessStrategyBasicImpl;
import org.hibernate.property.access.spi.PropertyAccess;
import org.hibernate.query.sqm.sql.SqmToSqlAstConverter;
import org.hibernate.spi.NavigablePath;
import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.SqlAstJoinType;
import org.hibernate.sql.ast.spi.FromClauseAccess;
import org.hibernate.sql.ast.spi.SqlAliasBase;
import org.hibernate.sql.ast.spi.SqlAstCreationState;
import org.hibernate.sql.ast.spi.SqlExpressionResolver;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.SqlTuple;
import org.hibernate.sql.ast.tree.from.PluralTableGroup;
import org.hibernate.sql.ast.tree.from.StandardVirtualTableGroup;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableGroupJoin;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.ast.tree.predicate.Predicate;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.Fetch;
import org.hibernate.sql.results.graph.FetchOptions;
import org.hibernate.sql.results.graph.FetchParent;
import org.hibernate.sql.results.graph.Fetchable;
import org.hibernate.sql.results.graph.embeddable.EmbeddableValuedFetchable;
import org.hibernate.sql.results.graph.embeddable.internal.EmbeddableFetchImpl;
import org.hibernate.sql.results.graph.embeddable.internal.EmbeddableResultImpl;
import org.hibernate.type.descriptor.java.JavaType;

/**
 * @author Steve Ebersole
 */
public class EmbeddedCollectionPart implements CollectionPart, EmbeddableValuedFetchable, FetchOptions {
	private final NavigableRole navigableRole;
	private final CollectionPersister collectionDescriptor;
	private final Nature nature;
	private final EmbeddableMappingType embeddableMappingType;

	private final String containingTableExpression;

	private final PropertyAccess parentInjectionAttributePropertyAccess;
	private final String sqlAliasStem;

	public EmbeddedCollectionPart(
			CollectionPersister collectionDescriptor,
			Nature nature,
			EmbeddableMappingType embeddableMappingType,
			String parentInjectionAttributeName,
			String containingTableExpression,
			String sqlAliasStem) {
		this.navigableRole = collectionDescriptor.getNavigableRole().appendContainer( nature.getName() );
		this.collectionDescriptor = collectionDescriptor;
		this.nature = nature;
		if ( parentInjectionAttributeName != null ) {
			parentInjectionAttributePropertyAccess = PropertyAccessStrategyBasicImpl.INSTANCE.buildPropertyAccess(
					embeddableMappingType.getMappedJavaType().getJavaTypeClass(),
					parentInjectionAttributeName,
					true );
		}
		else {
			parentInjectionAttributePropertyAccess = null;
		}
		this.embeddableMappingType = embeddableMappingType;

		this.containingTableExpression = containingTableExpression;
		this.sqlAliasStem = sqlAliasStem;
	}

	@Override
	public <T> DomainResult<T> createDomainResult(
			NavigablePath navigablePath,
			TableGroup tableGroup,
			String resultVariable,
			DomainResultCreationState creationState) {
		// Make sure the pre-created table group for the part is registered under its navigable path
		resolveTableGroup( navigablePath, creationState );
		return new EmbeddableResultImpl<>(
				navigablePath,
				this,
				resultVariable,
				creationState
		);
	}

	@Override
	public Nature getNature() {
		return nature;
	}

	@Override
	public EmbeddableMappingType getEmbeddableTypeDescriptor() {
		return embeddableMappingType;
	}

	@Override
	public MappingType getPartMappingType() {
		return getEmbeddableTypeDescriptor();
	}

	@Override
	public String getContainingTableExpression() {
		return containingTableExpression;
	}

	@Override
	public PropertyAccess getParentInjectionAttributePropertyAccess() {
		return parentInjectionAttributePropertyAccess;
	}

	@Override
	public String getFetchableName() {
		return getNature().getName();
	}

	@Override
	public int getFetchableKey() {
		return nature == Nature.INDEX || !collectionDescriptor.hasIndex() ? 0 : 1;
	}

	@Override
	public FetchOptions getMappedFetchOptions() {
		return this;
	}

	@Override
	public String toString() {
		return "EmbeddedCollectionPart(" + getNavigableRole() + ")@" + System.identityHashCode( this );
	}

	@Override
	public Fetch generateFetch(
			FetchParent fetchParent,
			NavigablePath fetchablePath,
			FetchTiming fetchTiming,
			boolean selected,
			String resultVariable,
			DomainResultCreationState creationState) {
		// Make sure the pre-created table group for the part is registered under its navigable path
		resolveTableGroup( fetchablePath, creationState );
		return new EmbeddableFetchImpl(
				fetchablePath,
				this,
				fetchParent,
				FetchTiming.IMMEDIATE,
				selected,
				creationState
		);
	}

	private TableGroup resolveTableGroup(NavigablePath fetchablePath, DomainResultCreationState creationState) {
		final FromClauseAccess fromClauseAccess = creationState.getSqlAstCreationState().getFromClauseAccess();
		return fromClauseAccess.resolveTableGroup(
				fetchablePath,
				np -> {
					final PluralTableGroup parentTableGroup = (PluralTableGroup) fromClauseAccess.getTableGroup( np.getParent() );
					switch ( nature ) {
						case ELEMENT:
							return parentTableGroup.getElementTableGroup();
						case INDEX:
							return parentTableGroup.getIndexTableGroup();
					}

					throw new IllegalStateException( "Could not find table group for: " + np );
				}
		);
	}

	@Override
	public SqlTuple toSqlExpression(
			TableGroup tableGroup,
			Clause clause,
			SqmToSqlAstConverter walker,
			SqlAstCreationState sqlAstCreationState) {
		final SqlExpressionResolver sqlExpressionResolver = sqlAstCreationState.getSqlExpressionResolver();

		final List<Expression> expressions = new ArrayList<>();
		getEmbeddableTypeDescriptor().forEachSelectable(
				(columnIndex, selection) -> {
					assert containingTableExpression.equals( selection.getContainingTableExpression() );
					final TableReference tableReference = tableGroup.resolveTableReference(
							tableGroup.getNavigablePath()
									.append( getNavigableRole().getNavigableName() ),
							selection.getContainingTableExpression()
					);
					expressions.add(
							sqlExpressionResolver.resolveSqlExpression(
									tableReference,
									selection
							)
					);
				}
		);
		return new SqlTuple( expressions, this );
	}

	@Override
	public TableGroupJoin createTableGroupJoin(
			NavigablePath navigablePath,
			TableGroup lhs,
			String explicitSourceAlias,
			SqlAliasBase explicitSqlAliasBase,
			SqlAstJoinType requestedJoinType,
			boolean fetched,
			boolean addsPredicate,
			SqlAstCreationState creationState) {
		final SqlAstJoinType joinType;
		if ( requestedJoinType == null ) {
			joinType = SqlAstJoinType.INNER;
		}
		else {
			joinType = requestedJoinType;
		}
		final TableGroup tableGroup = createRootTableGroupJoin(
				navigablePath,
				lhs,
				explicitSourceAlias,
				explicitSqlAliasBase,
				requestedJoinType,
				fetched,
				null,
				creationState
		);

		return new TableGroupJoin( navigablePath, joinType, tableGroup, null );
	}

	@Override
	public TableGroup createRootTableGroupJoin(
			NavigablePath navigablePath,
			TableGroup lhs,
			String explicitSourceAlias,
			SqlAliasBase explicitSqlAliasBase,
			SqlAstJoinType sqlAstJoinType,
			boolean fetched,
			Consumer<Predicate> predicateConsumer,
			SqlAstCreationState creationState) {
		assert lhs.getModelPart() instanceof PluralAttributeMapping;
		return new StandardVirtualTableGroup( navigablePath, this, lhs, fetched );
	}

	@Override
	public String getSqlAliasStem() {
		return sqlAliasStem;
	}

	@Override
	public ModelPart findSubPart(String name, EntityMappingType treatTargetType) {
		return getEmbeddableTypeDescriptor().findSubPart( name, treatTargetType );
	}

	@Override
	public void visitSubParts(Consumer<ModelPart> consumer, EntityMappingType treatTargetType) {
		getEmbeddableTypeDescriptor().visitSubParts( consumer, treatTargetType );
	}

	@Override
	public void applySqlSelections(
			NavigablePath navigablePath,
			TableGroup tableGroup,
			DomainResultCreationState creationState) {
		embeddableMappingType.applySqlSelections( navigablePath, tableGroup, creationState );
	}

	@Override
	public void applySqlSelections(
			NavigablePath navigablePath,
			TableGroup tableGroup,
			DomainResultCreationState creationState,
			BiConsumer<SqlSelection, JdbcMapping> selectionConsumer) {
		embeddableMappingType.applySqlSelections( navigablePath, tableGroup, creationState, selectionConsumer );
	}

	@Override
	public JavaType<?> getExpressibleJavaType() {
		return getJavaType();
	}

	@Override
	public NavigableRole getNavigableRole() {
		return navigableRole;
	}

	@Override
	public EntityMappingType findContainingEntityMapping() {
		return collectionDescriptor.getAttributeMapping().findContainingEntityMapping();
	}

	@Override
	public FetchStyle getStyle() {
		return FetchStyle.JOIN;
	}

	@Override
	public FetchTiming getTiming() {
		return FetchTiming.IMMEDIATE;
	}

}
