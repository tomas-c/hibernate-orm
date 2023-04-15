/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.mapping.internal;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;

import org.hibernate.cache.MutableCacheKeyBuilder;
import org.hibernate.engine.FetchStyle;
import org.hibernate.engine.FetchTiming;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.internal.util.IndexedConsumer;
import org.hibernate.metamodel.mapping.AssociationKey;
import org.hibernate.metamodel.mapping.BasicValuedModelPart;
import org.hibernate.metamodel.mapping.EntityIdentifierMapping;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.ForeignKeyDescriptor;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.JdbcMappingContainer;
import org.hibernate.metamodel.mapping.ManagedMappingType;
import org.hibernate.metamodel.mapping.MappingType;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.metamodel.mapping.PropertyBasedMapping;
import org.hibernate.metamodel.mapping.SelectableConsumer;
import org.hibernate.metamodel.mapping.SelectableMapping;
import org.hibernate.metamodel.model.domain.NavigableRole;
import org.hibernate.property.access.spi.PropertyAccess;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.hibernate.query.sqm.ComparisonOperator;
import org.hibernate.spi.NavigablePath;
import org.hibernate.sql.ast.spi.SqlAstCreationState;
import org.hibernate.sql.ast.spi.SqlExpressionResolver;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.expression.ColumnReference;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableGroupProducer;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.ast.tree.from.UnknownTableReferenceException;
import org.hibernate.sql.ast.tree.predicate.ComparisonPredicate;
import org.hibernate.sql.ast.tree.predicate.Predicate;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.Fetch;
import org.hibernate.sql.results.graph.FetchOptions;
import org.hibernate.sql.results.graph.FetchParent;
import org.hibernate.sql.results.graph.basic.BasicResult;
import org.hibernate.type.descriptor.converter.spi.BasicValueConverter;
import org.hibernate.type.descriptor.java.JavaType;

/**
 * @author Steve Ebersole
 */
public class SimpleForeignKeyDescriptor implements ForeignKeyDescriptor, BasicValuedModelPart, FetchOptions {
	private final SimpleForeignKeyDescriptorSide keySide;
	private final SimpleForeignKeyDescriptorSide targetSide;

	private final boolean refersToPrimaryKey;
	private final boolean hasConstraint;
	private AssociationKey associationKey;

	public SimpleForeignKeyDescriptor(
			ManagedMappingType keyDeclaringType,
			BasicValuedModelPart keyModelPart,
			PropertyAccess keyPropertyAccess,
			SelectableMapping keySelectableMapping,
			BasicValuedModelPart targetModelPart,
			boolean insertable,
			boolean updateable,
			boolean refersToPrimaryKey,
			boolean hasConstraint) {
		this(
				BasicAttributeMapping.withSelectableMapping(
						keyDeclaringType,
						keyModelPart,
						keyPropertyAccess,
						insertable,
						updateable,
						keySelectableMapping
				),
				targetModelPart,
				refersToPrimaryKey,
				hasConstraint,
				false
		);
	}

	public SimpleForeignKeyDescriptor(
			BasicValuedModelPart keyModelPart,
			BasicValuedModelPart targetModelPart,
			boolean refersToPrimaryKey,
			boolean hasConstraint,
			boolean swapDirection) {
		assert targetModelPart != null;

		if ( swapDirection ) {
			this.keySide = new SimpleForeignKeyDescriptorSide( Nature.KEY, targetModelPart );
			this.targetSide = new SimpleForeignKeyDescriptorSide( Nature.TARGET, keyModelPart );
		}
		else {
			this.keySide = new SimpleForeignKeyDescriptorSide( Nature.KEY, keyModelPart );
			this.targetSide = new SimpleForeignKeyDescriptorSide( Nature.TARGET, targetModelPart );
		}

		this.refersToPrimaryKey = refersToPrimaryKey;
		this.hasConstraint = hasConstraint;
	}

	public SimpleForeignKeyDescriptor(
			ManagedMappingType keyDeclaringType,
			SelectableMapping keySelectableMapping,
			BasicValuedModelPart targetModelPart,
			boolean refersToPrimaryKey,
			boolean hasConstraint) {
		this(
				keyDeclaringType,
				keySelectableMapping,
				( (PropertyBasedMapping) targetModelPart ).getPropertyAccess(),
				targetModelPart,
				refersToPrimaryKey,
				hasConstraint,
				false
		);
	}

	/**
	 *
	 */
	public SimpleForeignKeyDescriptor(
			ManagedMappingType keyDeclaringType,
			SelectableMapping keySelectableMapping,
			PropertyAccess valueAccess,
			BasicValuedModelPart targetModelPart,
			boolean refersToPrimaryKey,
			boolean hasConstraint,
			boolean swapDirection) {
		this(
				BasicAttributeMapping.withSelectableMapping(
						keyDeclaringType,
						targetModelPart,
						valueAccess,
						keySelectableMapping.isInsertable(),
						keySelectableMapping.isUpdateable(),
						keySelectableMapping
				),
				targetModelPart,
				refersToPrimaryKey,
				hasConstraint,
				swapDirection
		);
	}

	@Override
	public String getKeyTable() {
		return keySide.getModelPart().getContainingTableExpression();
	}

	@Override
	public String getTargetTable() {
		return targetSide.getModelPart().getContainingTableExpression();
	}

	@Override
	public BasicValuedModelPart getKeyPart() {
		return keySide.getModelPart();
	}

	@Override
	public BasicValuedModelPart getTargetPart() {
		return targetSide.getModelPart();
	}

	@Override
	public Side getKeySide() {
		return keySide;
	}

	@Override
	public Side getTargetSide() {
		return targetSide;
	}

	@Override
	public int compare(Object key1, Object key2) {
		//noinspection unchecked,rawtypes
		return ( (JavaType) keySide.getModelPart().getJavaType() ).getComparator().compare( key1, key2 );
	}

	@Override
	public ForeignKeyDescriptor withKeySelectionMapping(
			ManagedMappingType declaringType,
			TableGroupProducer declaringTableGroupProducer,
			IntFunction<SelectableMapping> selectableMappingAccess,
			MappingModelCreationProcess creationProcess) {
		return new SimpleForeignKeyDescriptor(
				declaringType,
				keySide.getModelPart(),
				( (PropertyBasedMapping) keySide.getModelPart() ).getPropertyAccess(),
				selectableMappingAccess.apply( 0 ),
				targetSide.getModelPart(),
				keySide.getModelPart().isInsertable(),
				keySide.getModelPart().isUpdateable(),
				refersToPrimaryKey,
				hasConstraint
		);
	}

	@Override
	public DomainResult<?> createKeyDomainResult(
			NavigablePath navigablePath,
			TableGroup tableGroup,
			FetchParent fetchParent,
			DomainResultCreationState creationState) {
		return createDomainResult(
				navigablePath,
				tableGroup,
				keySide.getModelPart(),
				fetchParent,
				creationState
		);
	}

	@Override
	public DomainResult<?> createTargetDomainResult(
			NavigablePath navigablePath,
			TableGroup tableGroup,
			FetchParent fetchParent,
			DomainResultCreationState creationState) {
		return createDomainResult(
				navigablePath,
				tableGroup,
				targetSide.getModelPart(),
				fetchParent,
				creationState
		);
	}

	@Override
	public DomainResult<?> createDomainResult(
			NavigablePath navigablePath,
			TableGroup tableGroup,
			Nature side,
			FetchParent fetchParent, DomainResultCreationState creationState) {
		if ( side == Nature.KEY ) {
			return createDomainResult( navigablePath, tableGroup, keySide.getModelPart(), fetchParent, creationState );
		}
		else {
			return createDomainResult( navigablePath, tableGroup, targetSide.getModelPart(), fetchParent, creationState );
		}
	}

	@Override
	public <T> DomainResult<T> createDomainResult(
			NavigablePath navigablePath,
			TableGroup tableGroup,
			String resultVariable,
			DomainResultCreationState creationState) {
		return createDomainResult( navigablePath, tableGroup, keySide.getModelPart(), null, creationState );
	}

	@Override
	public void applySqlSelections(
			NavigablePath navigablePath,
			TableGroup tableGroup,
			DomainResultCreationState creationState) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void applySqlSelections(
			NavigablePath navigablePath,
			TableGroup tableGroup,
			DomainResultCreationState creationState,
			BiConsumer<SqlSelection, JdbcMapping> selectionConsumer) {
		throw new UnsupportedOperationException();
	}

	private <T> DomainResult<T> createDomainResult(
			NavigablePath navigablePath,
			TableGroup tableGroup,
			SelectableMapping selectableMapping,
			FetchParent fetchParent,
			DomainResultCreationState creationState) {
		final SqlAstCreationState sqlAstCreationState = creationState.getSqlAstCreationState();
		final SqlExpressionResolver sqlExpressionResolver = sqlAstCreationState.getSqlExpressionResolver();

		final NavigablePath resultNavigablePath;
		if ( selectableMapping == keySide.getModelPart() ) {
			resultNavigablePath = navigablePath.append( ForeignKeyDescriptor.PART_NAME );
		}
		else {
			resultNavigablePath = navigablePath.append( ForeignKeyDescriptor.TARGET_PART_NAME );
		}
		final TableReference tableReference;
		try {
			tableReference = tableGroup.resolveTableReference(
					resultNavigablePath,
					selectableMapping.getContainingTableExpression()
			);
		}
		catch (IllegalStateException tableNotFoundException) {
			throw new UnknownTableReferenceException(
					selectableMapping.getContainingTableExpression(),
					String.format(
							Locale.ROOT,
							"Unable to determine TableReference (`%s`) for `%s`",
							selectableMapping.getContainingTableExpression(),
							getNavigableRole().getFullPath()
					)
			);
		}

		final JavaType<?> javaType = selectableMapping.getJdbcMapping().getJdbcJavaType();
		final SqlSelection sqlSelection = sqlExpressionResolver.resolveSqlSelection(
				sqlExpressionResolver.resolveSqlExpression( tableReference, selectableMapping ),
				javaType,
				fetchParent,
				sqlAstCreationState.getCreationContext().getSessionFactory().getTypeConfiguration()
		);

		final JdbcMappingContainer selectionType = sqlSelection.getExpressionType();
		return new BasicResult<>(
				sqlSelection.getValuesArrayPosition(),
				null,
				selectableMapping.getJdbcMapping(),
				// if the expression type is different that the expected type coerce the value
				selectionType != null && selectionType.getSingleJdbcMapping().getJdbcJavaType() != javaType
		);
	}

	@Override
	public Predicate generateJoinPredicate(
			TableReference targetSideReference,
			TableReference keySideReference,
			SqlAstCreationState creationState) {
		return new ComparisonPredicate(
				new ColumnReference( targetSideReference, targetSide.getModelPart() ),
				ComparisonOperator.EQUAL,
				new ColumnReference( keySideReference, keySide.getModelPart() )
		);
	}

	@Override
	public Predicate generateJoinPredicate(
			TableGroup targetSideTableGroup,
			TableGroup keySideTableGroup,
			SqlAstCreationState creationState) {
		final TableReference lhsTableReference = targetSideTableGroup.resolveTableReference(
				targetSideTableGroup.getNavigablePath(),
				targetSide.getModelPart().getContainingTableExpression(),
				false
		);
		final TableReference rhsTableKeyReference = keySideTableGroup.resolveTableReference(
				null,
				keySide.getModelPart().getContainingTableExpression(),
				false
		);

		return generateJoinPredicate( lhsTableReference, rhsTableKeyReference, creationState );
	}

	@Override
	public boolean isSimpleJoinPredicate(Predicate predicate) {
		if ( !( predicate instanceof ComparisonPredicate ) ) {
			return false;
		}
		final ComparisonPredicate comparisonPredicate = (ComparisonPredicate) predicate;
		if ( comparisonPredicate.getOperator() != ComparisonOperator.EQUAL ) {
			return false;
		}
		final Expression lhsExpr = comparisonPredicate.getLeftHandExpression();
		final Expression rhsExpr = comparisonPredicate.getRightHandExpression();
		if ( !( lhsExpr instanceof ColumnReference ) || !( rhsExpr instanceof ColumnReference ) ) {
			return false;
		}
		final String lhs = ( (ColumnReference) lhsExpr ).getColumnExpression();
		final String rhs = ( (ColumnReference) rhsExpr ).getColumnExpression();
		final String keyExpression = keySide.getModelPart().getSelectionExpression();
		final String targetExpression = targetSide.getModelPart().getSelectionExpression();
		return ( lhs.equals( keyExpression ) && rhs.equals( targetExpression ) )
				|| ( lhs.equals( targetExpression ) && rhs.equals( keyExpression ) );
	}

	@Override
	public MappingType getPartMappingType() {
		return targetSide.getModelPart().getMappedType();
	}

	@Override
	public int forEachSelectable(int offset, SelectableConsumer consumer) {
		consumer.accept( offset, this );
		return 1;
	}

	@Override
	public JavaType<?> getJavaType() {
		return targetSide.getModelPart().getJdbcMapping().getJavaTypeDescriptor();
	}

	@Override
	public NavigableRole getNavigableRole() {
		return targetSide.getModelPart().getNavigableRole();
	}

	@Override
	public EntityMappingType findContainingEntityMapping() {
		return targetSide.getModelPart().findContainingEntityMapping();
	}

	@Override
	public Object disassemble(Object value, SharedSessionContractImplementor session) {
		return getJdbcMapping().convertToRelationalValue( value );
	}

	@Override
	public void addToCacheKey(MutableCacheKeyBuilder cacheKey, Object value, SharedSessionContractImplementor session) {
		if ( value == null ) {
			return;
		}
		final JdbcMapping jdbcMapping = getJdbcMapping();
		final BasicValueConverter converter = jdbcMapping.getValueConverter();
		final Serializable disassemble;
		final int hashCode;
		if ( converter == null ) {
			final JavaType javaTypeDescriptor = jdbcMapping.getJavaTypeDescriptor();
			disassemble = javaTypeDescriptor.getMutabilityPlan().disassemble( value, session );
			hashCode = javaTypeDescriptor.extractHashCode( disassemble );
		}
		else {
			final Object relationalValue = converter.toRelationalValue( value );
			final JavaType relationalJavaType = converter.getRelationalJavaType();
			disassemble = relationalJavaType.getMutabilityPlan().disassemble( relationalValue, session );
			hashCode = relationalJavaType.extractHashCode( relationalValue );
		}

		cacheKey.addValue( disassemble );
		cacheKey.addHashCode( hashCode );
	}

	@Override
	public Object getAssociationKeyFromSide(
			Object targetObject,
			ForeignKeyDescriptor.Side side,
			SharedSessionContractImplementor session) {
		if ( targetObject == null ) {
			return null;
		}
		if ( refersToPrimaryKey ) {
			final LazyInitializer lazyInitializer = HibernateProxy.extractLazyInitializer( targetObject );
			if ( lazyInitializer != null ) {
				return lazyInitializer.getIdentifier();
			}
		}
		final ModelPart modelPart = side.getModelPart();
		if ( modelPart.isEntityIdentifierMapping() ) {
			return ( (EntityIdentifierMapping) modelPart ).getIdentifierIfNotUnsaved( targetObject, session );
		}
		return ( (PropertyBasedMapping) modelPart ).getPropertyAccess().getGetter().get( targetObject );
	}

	@Override
	public <X, Y> int forEachDisassembledJdbcValue(
			Object value,
			int offset,
			X x,
			Y y,
			JdbcValuesBiConsumer<X, Y> valuesConsumer,
			SharedSessionContractImplementor session) {
		valuesConsumer.consume( offset, x, y, value, getJdbcMapping() );
		return getJdbcTypeCount();
	}

	@Override
	public <X, Y> int breakDownJdbcValues(
			Object domainValue,
			int offset,
			X x,
			Y y,
			JdbcValueBiConsumer<X, Y> valueConsumer,
			SharedSessionContractImplementor session) {
		valueConsumer.consume( offset, x, y, disassemble( domainValue, session ), keySide.getModelPart() );
		return getJdbcTypeCount();
	}

	@Override
	public int visitKeySelectables(int offset, SelectableConsumer consumer) {
		consumer.accept( offset, keySide.getModelPart() );
		return getJdbcTypeCount();
	}

	@Override
	public int visitTargetSelectables(int offset, SelectableConsumer consumer) {
		consumer.accept( offset, targetSide.getModelPart() );
		return getJdbcTypeCount();
	}

	@Override
	public boolean hasConstraint() {
		return hasConstraint;
	}

	@Override
	public AssociationKey getAssociationKey() {
		if ( associationKey == null ) {
			final List<String> associationKeyColumns = Collections.singletonList( keySide.getModelPart().getSelectionExpression() );
			associationKey = new AssociationKey( keySide.getModelPart().getContainingTableExpression(), associationKeyColumns );
		}
		return associationKey;
	}

	@Override
	public List<JdbcMapping> getJdbcMappings() {
		return Collections.singletonList( targetSide.getModelPart().getJdbcMapping() );
	}


	@Override
	public JdbcMapping getJdbcMapping(int index) {
		if ( index != 0 ) {
			throw new IndexOutOfBoundsException( index );
		}
		return targetSide.getModelPart().getJdbcMapping();
	}
	@Override
	public JdbcMapping getSingleJdbcMapping() {
		return targetSide.getModelPart().getJdbcMapping();
	}

	@Override
	public int forEachJdbcType(int offset, IndexedConsumer<JdbcMapping> action) {
		action.accept( offset, targetSide.getModelPart().getJdbcMapping() );
		return getJdbcTypeCount();
	}

	@Override
	public <X, Y> int forEachJdbcValue(
			Object value,
			int offset,
			X x,
			Y y,
			JdbcValuesBiConsumer<X, Y> valuesConsumer,
			SharedSessionContractImplementor session) {
		valuesConsumer.consume( offset, x, y, disassemble( value, session ), targetSide.getModelPart().getJdbcMapping() );
		return getJdbcTypeCount();
	}


	@Override
	public String getContainingTableExpression() {
		return keySide.getModelPart().getContainingTableExpression();
	}

	@Override
	public String getSelectionExpression() {
		return keySide.getModelPart().getSelectionExpression();
	}

	@Override
	public SelectableMapping getSelectable(int columnIndex) {
		return keySide.getModelPart();
	}

	@Override
	public boolean isFormula() {
		return keySide.getModelPart().isFormula();
	}

	@Override
	public boolean isNullable() {
		return keySide.getModelPart().isNullable();
	}

	@Override
	public boolean isInsertable() {
		return keySide.getModelPart().isInsertable();
	}

	@Override
	public boolean isUpdateable() {
		return keySide.getModelPart().isUpdateable();
	}

	@Override
	public boolean isPartitioned() {
		return keySide.getModelPart().isPartitioned();
	}

	@Override
	public String getCustomReadExpression() {
		return keySide.getModelPart().getCustomReadExpression();
	}

	@Override
	public String getCustomWriteExpression() {
		return keySide.getModelPart().getCustomWriteExpression();
	}

	@Override
	public String getColumnDefinition() {
		return keySide.getModelPart().getColumnDefinition();
	}

	@Override
	public Long getLength() {
		return keySide.getModelPart().getLength();
	}

	@Override
	public Integer getPrecision() {
		return keySide.getModelPart().getPrecision();
	}

	@Override
	public Integer getScale() {
		return keySide.getModelPart().getScale();
	}

	@Override
	public String getFetchableName() {
		return PART_NAME;
	}

	@Override
	public int getFetchableKey() {
		throw new UnsupportedOperationException();
	}

	@Override
	public FetchOptions getMappedFetchOptions() {
		return this;
	}

	@Override
	public FetchStyle getStyle() {
		return FetchStyle.JOIN;
	}

	@Override
	public FetchTiming getTiming() {
		return FetchTiming.IMMEDIATE;
	}

	@Override
	public Fetch generateFetch(
			FetchParent fetchParent,
			NavigablePath fetchablePath,
			FetchTiming fetchTiming,
			boolean selected,
			String resultVariable,
			DomainResultCreationState creationState) {
		return null;
	}

	@Override
	public MappingType getMappedType() {
		return null;
	}

	@Override
	public JdbcMapping getJdbcMapping() {
		return keySide.getModelPart().getJdbcMapping();
	}

	@Override
	public String toString() {
		return String.format(
				"SimpleForeignKeyDescriptor : %s.%s -> %s.%s",
				keySide.getModelPart().getContainingTableExpression(),
				keySide.getModelPart().getSelectionExpression(),
				targetSide.getModelPart().getContainingTableExpression(),
				targetSide.getModelPart().getSelectionExpression()
		);
	}
}
