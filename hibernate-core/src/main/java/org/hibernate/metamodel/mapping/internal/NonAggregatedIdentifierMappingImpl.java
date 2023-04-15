/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.mapping.internal;

import java.util.List;
import java.util.function.BiConsumer;

import org.hibernate.cache.MutableCacheKeyBuilder;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.PersistenceContext;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.internal.util.collections.CollectionHelper;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.RootClass;
import org.hibernate.metamodel.internal.AbstractCompositeIdentifierMapping;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.EmbeddableMappingType;
import org.hibernate.metamodel.mapping.EntityIdentifierMapping;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.metamodel.mapping.NonAggregatedIdentifierMapping;
import org.hibernate.metamodel.mapping.SelectableMappings;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.query.sqm.sql.SqmToSqlAstConverter;
import org.hibernate.spi.NavigablePath;
import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.spi.SqlAstCreationState;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.expression.ColumnReference;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.SqlTuple;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.Fetchable;

/**
 * A "non-aggregated" composite identifier.
 *
 * This is an identifier defined using more than one {@link jakarta.persistence.Id}
 * attribute with zero-or-more {@link jakarta.persistence.MapsId}.
 *
 * Can also be a single {@link jakarta.persistence.Id} with {@link jakarta.persistence.MapsId}
 */
public class NonAggregatedIdentifierMappingImpl extends AbstractCompositeIdentifierMapping implements NonAggregatedIdentifierMapping {
	private final EntityPersister entityDescriptor;

	private final VirtualIdEmbeddable virtualIdEmbeddable;
	private final IdClassEmbeddable idClassEmbeddable;

	private final IdentifierValueMapper identifierValueMapper;

	public NonAggregatedIdentifierMappingImpl(
			EntityPersister entityPersister,
			RootClass bootEntityDescriptor,
			String rootTableName,
			String[] rootTableKeyColumnNames,
			MappingModelCreationProcess creationProcess) {
		super( entityPersister, rootTableName, creationProcess );
		entityDescriptor = entityPersister;

		if ( bootEntityDescriptor.getIdentifierMapper() == null
				|| bootEntityDescriptor.getIdentifierMapper() == bootEntityDescriptor.getIdentifier() ) {
			// cid -> getIdentifier
			// idClass -> null
			final Component virtualIdSource = (Component) bootEntityDescriptor.getIdentifier();

			virtualIdEmbeddable = new VirtualIdEmbeddable(
					virtualIdSource,
					this,
					entityPersister,
					rootTableName,
					rootTableKeyColumnNames,
					creationProcess
			);
			idClassEmbeddable = null;
			identifierValueMapper = virtualIdEmbeddable;
		}
		else {
			// cid = getIdentifierMapper
			// idClass = getIdentifier
			final Component virtualIdSource = bootEntityDescriptor.getIdentifierMapper();
			final Component idClassSource = (Component) bootEntityDescriptor.getIdentifier();

			virtualIdEmbeddable = new VirtualIdEmbeddable(
					virtualIdSource,
					this,
					entityPersister,
					rootTableName,
					rootTableKeyColumnNames,
					creationProcess
			);
			idClassEmbeddable = new IdClassEmbeddable(
					idClassSource,
					bootEntityDescriptor,
					this,
					entityPersister,
					rootTableName,
					rootTableKeyColumnNames,
					virtualIdEmbeddable,
					creationProcess
			);
			identifierValueMapper = idClassEmbeddable;
		}
	}

	@Override
	public EmbeddableMappingType getMappedType() {
		return virtualIdEmbeddable;
	}

	@Override
	public EmbeddableMappingType getPartMappingType() {
		return getMappedType();
	}

	@Override
	public IdClassEmbeddable getIdClassEmbeddable() {
		return idClassEmbeddable;
	}

	@Override
	public VirtualIdEmbeddable getVirtualIdEmbeddable() {
		return virtualIdEmbeddable;
	}

	@Override
	public IdentifierValueMapper getIdentifierValueMapper() {
		return identifierValueMapper;
	}

	@Override
	public boolean hasContainingClass() {
		return idClassEmbeddable != null;
	}

	@Override
	public EmbeddableMappingType getMappedIdEmbeddableTypeDescriptor() {
		return identifierValueMapper;
	}

	@Override
	public Object disassemble(Object value, SharedSessionContractImplementor session) {
		return identifierValueMapper.disassemble( value, session );
	}

	@Override
	public void addToCacheKey(MutableCacheKeyBuilder cacheKey, Object value, SharedSessionContractImplementor session) {
		identifierValueMapper.addToCacheKey( cacheKey, value, session );
	}

	@Override
	public <X, Y> int forEachJdbcValue(
			Object value,
			int offset,
			X x, Y y, JdbcValuesBiConsumer<X, Y> valuesConsumer,
			SharedSessionContractImplementor session) {
		return identifierValueMapper.forEachJdbcValue( value, offset, x, y, valuesConsumer, session );
	}

	@Override
	public SqlTuple toSqlExpression(
			TableGroup tableGroup,
			Clause clause,
			SqmToSqlAstConverter walker,
			SqlAstCreationState sqlAstCreationState) {
		if ( hasContainingClass() ) {
			final SelectableMappings selectableMappings = getEmbeddableTypeDescriptor();
			final List<ColumnReference> columnReferences = CollectionHelper.arrayList( selectableMappings.getJdbcTypeCount() );
			final NavigablePath navigablePath = tableGroup.getNavigablePath()
					.append( getNavigableRole().getNavigableName() );
			final TableReference defaultTableReference = tableGroup.resolveTableReference(
					navigablePath,
					getContainingTableExpression()
			);
			identifierValueMapper.forEachSelectable(
					0,
					(columnIndex, selection) -> {
						final TableReference tableReference = defaultTableReference.resolveTableReference( selection.getContainingTableExpression() ) != null
								? defaultTableReference
								: tableGroup.resolveTableReference(
								navigablePath,
								selection.getContainingTableExpression()
						);
						final Expression columnReference = sqlAstCreationState.getSqlExpressionResolver()
								.resolveSqlExpression( tableReference, selection );

						columnReferences.add( (ColumnReference) columnReference );
					}
			);

			return new SqlTuple( columnReferences, this );
		}
		return super.toSqlExpression( tableGroup, clause, walker, sqlAstCreationState );
	}

	@Override
	public Nature getNature() {
		return Nature.VIRTUAL;
	}

	@Override
	public String getAttributeName() {
		return null;
	}

	@Override
	public Object getIdentifier(Object entity) {
		if ( hasContainingClass() ) {
			final Object id = identifierValueMapper.getRepresentationStrategy().getInstantiator().instantiate(
					null,
					sessionFactory
			);
			final EmbeddableMappingType embeddableTypeDescriptor = getEmbeddableTypeDescriptor();
			final Object[] propertyValues = new Object[embeddableTypeDescriptor.getNumberOfAttributeMappings()];
			for ( int i = 0; i < propertyValues.length; i++ ) {
				final AttributeMapping attributeMapping = embeddableTypeDescriptor.getAttributeMapping( i );
				final Object o = attributeMapping.getPropertyAccess().getGetter().get( entity );
				if ( o == null ) {
					final AttributeMapping idClassAttributeMapping = identifierValueMapper.getAttributeMapping( i );
					if ( idClassAttributeMapping.getPropertyAccess().getGetter().getReturnTypeClass().isPrimitive() ) {
						propertyValues[i] = idClassAttributeMapping.getExpressibleJavaType().getDefaultValue();
					}
					else {
						propertyValues[i] = null;
					}
				}
				//JPA 2 @MapsId + @IdClass points to the pk of the entity
				else if ( attributeMapping instanceof ToOneAttributeMapping
						&& !( identifierValueMapper.getAttributeMapping( i ) instanceof ToOneAttributeMapping ) ) {
					final ToOneAttributeMapping toOneAttributeMapping = (ToOneAttributeMapping) attributeMapping;
					final ModelPart targetPart = toOneAttributeMapping.getForeignKeyDescriptor().getPart(
							toOneAttributeMapping.getSideNature().inverse()
					);
					if ( targetPart.isEntityIdentifierMapping() ) {
						propertyValues[i] = ( (EntityIdentifierMapping) targetPart ).getIdentifier( o );
					}
					else {
						propertyValues[i] = o;
						assert false;
					}
				}
				else {
					propertyValues[i] = o;
				}
			}
			identifierValueMapper.setValues( id, propertyValues );
			return id;
		}
		else {
			return entity;
		}
	}

	@Override
	public void setIdentifier(Object entity, Object id, SharedSessionContractImplementor session) {
		final Object[] propertyValues = new Object[identifierValueMapper.getNumberOfAttributeMappings()];
		final EmbeddableMappingType embeddableTypeDescriptor = getEmbeddableTypeDescriptor();
		for ( int i = 0; i < propertyValues.length; i++ ) {
			final AttributeMapping attribute = embeddableTypeDescriptor.getAttributeMapping( i );
			final AttributeMapping mappedIdAttributeMapping = identifierValueMapper.getAttributeMapping( i );
			Object o = mappedIdAttributeMapping.getPropertyAccess().getGetter().get( id );
			if ( attribute instanceof ToOneAttributeMapping && !( mappedIdAttributeMapping instanceof ToOneAttributeMapping ) ) {
				final ToOneAttributeMapping toOneAttributeMapping = (ToOneAttributeMapping) attribute;
				final EntityPersister entityPersister = toOneAttributeMapping.getEntityMappingType()
						.getEntityPersister();
				final EntityKey entityKey = session.generateEntityKey( o, entityPersister );
				final PersistenceContext persistenceContext = session.getPersistenceContext();
				// it is conceivable there is a proxy, so check that first
				o = persistenceContext.getProxy( entityKey );
				if ( o == null ) {
					// otherwise look for an initialized version
					o = persistenceContext.getEntity( entityKey );
					if ( o == null ) {
						// get the association out of the entity itself
						o = entityDescriptor.getPropertyValue(
								entity,
								toOneAttributeMapping.getAttributeName()
						);
					}
				}
			}
			propertyValues[i] = o;
		}
		embeddableTypeDescriptor.setValues( entity, propertyValues );
	}

	@Override
	public <X, Y> int breakDownJdbcValues(
			Object domainValue,
			int offset,
			X x,
			Y y,
			JdbcValueBiConsumer<X, Y> valueConsumer, SharedSessionContractImplementor session) {
		return identifierValueMapper.breakDownJdbcValues( domainValue, offset, x, y, valueConsumer, session );
	}

	@Override
	public void applySqlSelections(
			NavigablePath navigablePath,
			TableGroup tableGroup,
			DomainResultCreationState creationState) {
		identifierValueMapper.applySqlSelections( navigablePath, tableGroup, creationState );
	}

	@Override
	public void applySqlSelections(
			NavigablePath navigablePath,
			TableGroup tableGroup,
			DomainResultCreationState creationState,
			BiConsumer<SqlSelection, JdbcMapping> selectionConsumer) {
		identifierValueMapper.applySqlSelections( navigablePath, tableGroup, creationState, selectionConsumer );
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// EmbeddableValuedFetchable

	@Override
	public String getSqlAliasStem() {
		return "id";
	}

	@Override
	public String getFetchableName() {
		return EntityIdentifierMapping.ROLE_LOCAL_NAME;
	}

	@Override
	public int getNumberOfFetchables() {
		return getPartMappingType().getNumberOfFetchables();
	}

	@Override
	public Fetchable getFetchable(int position) {
		return getPartMappingType().getFetchable( position );
	}
}
