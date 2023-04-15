/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.metamodel.mapping.internal;

import java.util.Locale;
import java.util.function.Consumer;

import org.hibernate.annotations.NotFoundAction;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.IndexedCollection;
import org.hibernate.mapping.ManyToOne;
import org.hibernate.mapping.Map;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Value;
import org.hibernate.metamodel.mapping.AssociationKey;
import org.hibernate.metamodel.mapping.BasicEntityIdentifierMapping;
import org.hibernate.metamodel.mapping.BasicValuedModelPart;
import org.hibernate.metamodel.mapping.CompositeIdentifierMapping;
import org.hibernate.metamodel.mapping.EmbeddableValuedModelPart;
import org.hibernate.metamodel.mapping.EntityAssociationMapping;
import org.hibernate.metamodel.mapping.EntityIdentifierMapping;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.ForeignKeyDescriptor;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.metamodel.mapping.ModelPartContainer;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.metamodel.mapping.SelectableConsumer;
import org.hibernate.metamodel.mapping.SelectableMapping;
import org.hibernate.metamodel.mapping.SelectableMappings;
import org.hibernate.metamodel.mapping.ValuedModelPart;
import org.hibernate.metamodel.mapping.VirtualModelPart;
import org.hibernate.persister.collection.BasicCollectionPersister;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.collection.mutation.CollectionMutationTarget;
import org.hibernate.spi.NavigablePath;
import org.hibernate.sql.ast.SqlAstJoinType;
import org.hibernate.sql.ast.spi.SqlAliasBase;
import org.hibernate.sql.ast.spi.SqlAstCreationState;
import org.hibernate.sql.ast.tree.from.LazyTableGroup;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableGroupJoin;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.ast.tree.predicate.Predicate;
import org.hibernate.type.EntityType;

import static java.util.Objects.requireNonNullElse;
import static org.hibernate.metamodel.mapping.internal.MappingModelCreationHelper.createInverseModelPart;
import static org.hibernate.metamodel.mapping.internal.MappingModelCreationHelper.getPropertyOrder;

/**
 * Entity-valued collection-part mapped through a join table.  Models both <ul>
 *     <li>{@link jakarta.persistence.ManyToMany} mappings</li>
 *     <li>{@link jakarta.persistence.OneToMany} with {@link jakarta.persistence.JoinTable} mappings</li>
 * </ul>
 *
 * ```
 * user( id, ... )
 * group( id, ... )
 * membership( user_fk, group_fk )
 *
 * `Group.membership`
 * 		table: membership
 * 		key: group_fk
 * 		element: user_fk
 * ```
 *
 * @author Steve Ebersole
 */
public class ManyToManyCollectionPart extends AbstractEntityCollectionPart implements EntityAssociationMapping {
	private ForeignKeyDescriptor foreignKey;
	private ValuedModelPart fkTargetModelPart;

	public ManyToManyCollectionPart(
			Nature nature,
			Collection collectionBootDescriptor,
			CollectionPersister collectionDescriptor,
			EntityMappingType associatedEntityDescriptor,
			MappingModelCreationProcess creationProcess) {
		this( nature, collectionBootDescriptor, collectionDescriptor, associatedEntityDescriptor, NotFoundAction.EXCEPTION,  creationProcess );
	}

	public ManyToManyCollectionPart(
			Nature nature,
			Collection collectionBootDescriptor,
			CollectionPersister collectionDescriptor,
			EntityMappingType associatedEntityDescriptor,
			NotFoundAction notFoundAction,
			MappingModelCreationProcess creationProcess) {
		super( nature, collectionBootDescriptor, collectionDescriptor, associatedEntityDescriptor, notFoundAction, creationProcess );
	}

	@Override
	public Cardinality getCardinality() {
		return Cardinality.MANY_TO_MANY;
	}

	@Override
	public ModelPart getInclusionCheckPart() {
		return getForeignKeyDescriptor().getKeyPart();
	}

	@Override
	protected AssociationKey resolveFetchAssociationKey() {
		assert getForeignKeyDescriptor() != null;
		return getForeignKeyDescriptor().getAssociationKey();
	}

	@Override
	public ModelPart findSubPart(String name, EntityMappingType targetType) {
		// Prefer resolving the key part of the foreign key rather than the target part if possible
		// to allow deferring the initialization of the target table group, omitting it if possible.
		// This is not possible for one-to-many associations because we need to create the target table group eagerly,
		// to preserve the cardinality. Also, the OneToManyTableGroup has no reference to the parent table group
		if ( getTargetKeyPropertyNames().contains( name ) ) {
			if ( fkTargetModelPart instanceof ToOneAttributeMapping ) {
				return ( (ToOneAttributeMapping) fkTargetModelPart ).findSubPart( name, targetType );
			}
			final ModelPart keyPart = foreignKey.getKeyPart();
			if ( keyPart instanceof EmbeddableValuedModelPart && keyPart instanceof VirtualModelPart ) {
				return ( (ModelPartContainer) keyPart ).findSubPart( name, targetType );
			}
			return keyPart;
		}

		return super.findSubPart( name, targetType );
	}

	@Override
	public <X, Y> int breakDownJdbcValues(
			Object domainValue,
			int offset,
			X x,
			Y y,
			JdbcValueBiConsumer<X, Y> valueConsumer, SharedSessionContractImplementor session) {
		return fkTargetModelPart.breakDownJdbcValues( domainValue, offset, x, y, valueConsumer, session );
	}

	@Override
	public SelectableMapping getSelectable(int columnIndex) {
		return foreignKey.getKeyPart().getSelectable( columnIndex );
	}

	@Override
	public String getContainingTableExpression() {
		return fkTargetModelPart.getContainingTableExpression();
	}

	@Override
	public int forEachSelectable(int offset, SelectableConsumer consumer) {
		foreignKey.getKeyPart().forEachSelectable( offset, consumer );
		return getJdbcTypeCount();
	}

	@Override
	public <X, Y> int decompose(
			Object domainValue,
			int offset,
			X x,
			Y y,
			JdbcValueBiConsumer<X, Y> valueConsumer,
			SharedSessionContractImplementor session) {
		return foreignKey.getKeyPart().decompose(
				foreignKey.getAssociationKeyFromSide( domainValue, foreignKey.getTargetSide(), session ),
				offset,
				x,
				y,
				valueConsumer,
				session
		);
	}



	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Association / TableGroupJoinProducer

	@Override
	public ForeignKeyDescriptor getForeignKeyDescriptor() {
		return foreignKey;
	}

	@Override
	public ForeignKeyDescriptor.Nature getSideNature() {
		return ForeignKeyDescriptor.Nature.KEY;
	}

	@Override
	public SqlAstJoinType getDefaultSqlAstJoinType(TableGroup parentTableGroup) {
		return SqlAstJoinType.INNER;
	}

	@Override
	public boolean isSimpleJoinPredicate(Predicate predicate) {
		return getForeignKeyDescriptor().isSimpleJoinPredicate( predicate );
	}

	@Override
	public boolean isReferenceToPrimaryKey() {
		return getForeignKeyDescriptor().getTargetPart().isEntityIdentifierMapping();
	}

	@Override
	public boolean isFkOptimizationAllowed() {
		return true;
	}

	@Override
	public ModelPart getKeyTargetMatchPart() {
		return fkTargetModelPart;
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
		final SqlAstJoinType joinType = requireNonNullElse( requestedJoinType, SqlAstJoinType.INNER );

		final LazyTableGroup lazyTableGroup = createRootTableGroupJoin(
				navigablePath,
				lhs,
				explicitSourceAlias,
				explicitSqlAliasBase,
				requestedJoinType,
				fetched,
				null,
				creationState
		);

		final TableGroupJoin join = new TableGroupJoin(
				navigablePath,
				joinType,
				lazyTableGroup,
				null
		);

		lazyTableGroup.setTableGroupInitializerCallback( (partTableGroup) -> {
			// `partTableGroup` is the association table group
			join.applyPredicate(
					foreignKey.generateJoinPredicate(
							partTableGroup.getPrimaryTableReference(),
							lhs.resolveTableReference( foreignKey.getKeyTable() ),
							creationState
					)
			);
		} );

		return join;
	}

	@Override
	public LazyTableGroup createRootTableGroupJoin(
			NavigablePath navigablePath,
			TableGroup lhs,
			String explicitSourceAlias,
			SqlAliasBase explicitSqlAliasBase,
			SqlAstJoinType requestedJoinType,
			boolean fetched,
			Consumer<Predicate> predicateConsumer,
			SqlAstCreationState creationState) {
		final SqlAstJoinType joinType = requireNonNullElse( requestedJoinType, SqlAstJoinType.INNER );
		final boolean canUseInnerJoin = joinType == SqlAstJoinType.INNER || lhs.canUseInnerJoins();
		final SqlAliasBase sqlAliasBase = SqlAliasBase.from(
				explicitSqlAliasBase,
				explicitSourceAlias,
				this,
				creationState.getSqlAliasBaseGenerator()
		);

		final LazyTableGroup lazyTableGroup = new LazyTableGroup(
				canUseInnerJoin,
				navigablePath,
				fetched,
				() -> createTableGroupInternal(
						canUseInnerJoin,
						navigablePath,
						fetched,
						null,
						sqlAliasBase,
						creationState
				),
				(np, tableExpression) -> {
					if ( ! foreignKey.getKeyTable().equals( tableExpression ) ) {
						return false;
					}

					if ( navigablePath.equals( np.getParent() ) ) {
						return getTargetKeyPropertyNames().contains( np.getLocalName() );
					}

					final String relativePath = np.relativize( navigablePath );
					if ( relativePath == null ) {
						return false;
					}

					// Empty relative path means the navigable paths are equal,
					// in which case we allow resolving the parent table group
					return relativePath.isEmpty() || getTargetKeyPropertyNames().contains( relativePath );
				},
				this,
				explicitSourceAlias,
				sqlAliasBase,
				creationState.getCreationContext().getSessionFactory(),
				lhs
		);

		if ( predicateConsumer != null ) {
			final TableReference keySideTableReference = lhs.resolveTableReference(
					navigablePath,
					foreignKey.getKeyTable()
			);

			lazyTableGroup.setTableGroupInitializerCallback(
					tableGroup -> predicateConsumer.accept(
							foreignKey.generateJoinPredicate(
									tableGroup.getPrimaryTableReference(),
									keySideTableReference,
									creationState
							)
					)
			);
		}

		return lazyTableGroup;
	}

	@Override
	public boolean hasPartitionedSelectionMapping() {
		return foreignKey.hasPartitionedSelectionMapping();
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Initialization

	@Override
	public boolean finishInitialization(
			CollectionPersister collectionDescriptor,
			Collection bootCollectionDescriptor,
			String fkTargetModelPartName,
			MappingModelCreationProcess creationProcess) {
		if ( fkTargetModelPartName != null ) {
			// @OneToMany + @JoinTable w/ @JoinColumn( referencedColumnName="fkTargetModelPartName" )
			fkTargetModelPart = resolveNamedTargetPart( fkTargetModelPartName, getAssociatedEntityMappingType(), collectionDescriptor );
		}
		else if ( getNature() == Nature.INDEX ) {
			assert bootCollectionDescriptor.isIndexed();

			final PluralAttributeMapping pluralAttribute = collectionDescriptor.getAttributeMapping();
			final String mapKeyPropertyName = ( (Map) bootCollectionDescriptor ).getMapKeyPropertyName();
			if ( StringHelper.isNotEmpty( mapKeyPropertyName ) ) {
				// @MapKey( name="fkTargetModelPartName" )
				final EntityCollectionPart elementDescriptor = (EntityCollectionPart) pluralAttribute.getElementDescriptor();
				final EntityMappingType entityMappingType = elementDescriptor.getEntityMappingType();
				fkTargetModelPart = resolveNamedTargetPart( mapKeyPropertyName, entityMappingType, collectionDescriptor );
			}
			else {
				fkTargetModelPart = getAssociatedEntityMappingType().getIdentifierMapping();
			}
		}
		else if ( StringHelper.isNotEmpty( bootCollectionDescriptor.getMappedByProperty() ) ) {
			final ModelPart mappedByPart = resolveNamedTargetPart( bootCollectionDescriptor.getMappedByProperty(), getAssociatedEntityMappingType(), collectionDescriptor );
			if ( mappedByPart instanceof ToOneAttributeMapping ) {
				////////////////////////////////////////////////
				// E.g.
				//
				// @Entity
				// class Book {
				// 	...
				// 	@ManyToOne(fetch = FetchType.LAZY)
				// 	@JoinTable(name = "author_book",
				// 	            joinColumns = @JoinColumn(name = "book_id"),
				// 	            inverseJoinColumns = @JoinColumn(name="author_id",nullable = false))
				// 	private Author author;
				// }
				//
				// @Entity
				// class Author {
				// 	...
				// 	@OneToMany(mappedBy = "author")
				// 	private List<Book> books;
				// }

				// create the foreign-key from the join-table (author_book) to the part table (Book) :
				//		`author_book.book_id -> Book.id`

				final ManyToOne elementDescriptor = (ManyToOne) bootCollectionDescriptor.getElement();
				assert elementDescriptor.isReferenceToPrimaryKey();

				final String collectionTableName = ( (BasicCollectionPersister) collectionDescriptor ).getTableName();

				// this fk will refer to the associated entity's id.  if that id is not ready yet, delay this creation
				if ( getAssociatedEntityMappingType().getIdentifierMapping() == null ) {
					return false;
				}

				foreignKey = createJoinTablePartForeignKey( collectionTableName, elementDescriptor, creationProcess );
				creationProcess.registerForeignKey( this, foreignKey );
			}
			else {
				final PluralAttributeMapping manyToManyInverse = (PluralAttributeMapping) mappedByPart;
				if ( manyToManyInverse.getKeyDescriptor() == null ) {
					// the collection-key is not yet ready, we need to wait
					return false;
				}
				foreignKey = manyToManyInverse.getKeyDescriptor();
			}

			fkTargetModelPart = foreignKey.getTargetPart();
			return true;
		}
		else {
			// non-inverse @ManyToMany
			fkTargetModelPart = getAssociatedEntityMappingType().getIdentifierMapping();
		}

		if ( getNature() == Nature.ELEMENT ) {
			foreignKey = createForeignKeyDescriptor(
					bootCollectionDescriptor.getElement(),
					(EntityType) collectionDescriptor.getElementType(),
					fkTargetModelPart,
					creationProcess,
					collectionDescriptor.getFactory().getJdbcServices().getDialect()
			);
		}
		else {
			assert bootCollectionDescriptor.isIndexed();
			foreignKey = createForeignKeyDescriptor(
					( (IndexedCollection) bootCollectionDescriptor ).getIndex(),
					(EntityType) collectionDescriptor.getIndexType(),
					fkTargetModelPart,
					creationProcess,
					collectionDescriptor.getFactory().getJdbcServices().getDialect()
			);
		}

		return true;
	}

	private ForeignKeyDescriptor createJoinTablePartForeignKey(
			String collectionTableName,
			ManyToOne elementBootDescriptor,
			MappingModelCreationProcess creationProcess) {
		final EntityMappingType associatedEntityMapping = getAssociatedEntityMappingType();
		final EntityIdentifierMapping associatedIdMapping = associatedEntityMapping.getIdentifierMapping();
		assert associatedIdMapping != null;

		// NOTE : `elementBootDescriptor` describes the key side of the fk
		// NOTE : `associatedIdMapping` is the target side model-part

		// we have the fk target model-part and selectables via the associated entity's id mapping
		// and need to create the inverse (key) selectable-mappings and composite model-part

		if ( associatedIdMapping.getNature() == EntityIdentifierMapping.Nature.SIMPLE ) {
			final BasicEntityIdentifierMapping targetModelPart = (BasicEntityIdentifierMapping) associatedIdMapping;

			assert elementBootDescriptor.getColumns().size() == 1;
			final Column keyColumn = elementBootDescriptor.getColumns().get( 0 );

			final SelectableMapping keySelectableMapping = SelectableMappingImpl.from(
					collectionTableName,
					keyColumn,
					targetModelPart.getJdbcMapping(),
					creationProcess.getCreationContext().getTypeConfiguration(),
					true,
					false,
					false,
					creationProcess.getCreationContext().getDialect(),
					creationProcess.getSqmFunctionRegistry()
			);

			final BasicAttributeMapping keyModelPart = BasicAttributeMapping.withSelectableMapping(
					associatedEntityMapping,
					targetModelPart,
					targetModelPart.getPropertyAccess(),
					true,
					false,
					keySelectableMapping
			);

			return new SimpleForeignKeyDescriptor(
					// the key
					keyModelPart,
					// the target
					targetModelPart,
					// refers to primary key
					true,
					!elementBootDescriptor.isNullable(),
					// do not swap the sides
					false
			);
		}
		else {
			final CompositeIdentifierMapping targetModelPart = (CompositeIdentifierMapping) associatedIdMapping;

			final SelectableMappings keySelectableMappings = SelectableMappingsImpl.from(
					collectionTableName,
					elementBootDescriptor,
					getPropertyOrder( elementBootDescriptor, creationProcess ),
					creationProcess.getCreationContext().getMetadata(),
					creationProcess.getCreationContext().getTypeConfiguration(),
					elementBootDescriptor.getColumnInsertability(),
					elementBootDescriptor.getColumnUpdateability(),
					creationProcess.getCreationContext().getDialect(),
					creationProcess.getSqmFunctionRegistry()
			);

			return new EmbeddedForeignKeyDescriptor(
					collectionTableName,
					keySelectableMappings,
					createInverseModelPart(
							targetModelPart,
							associatedEntityMapping,
							this,
							keySelectableMappings,
							creationProcess
					),
					targetModelPart.getContainingTableExpression(),
					targetModelPart.getPartMappingType(),
					targetModelPart,
					!elementBootDescriptor.isNullable(),
					creationProcess
			);
		}
	}

	private static ValuedModelPart resolveNamedTargetPart(
			String targetPartName,
			EntityMappingType entityMappingType,
			CollectionPersister collectionDescriptor) {
		final ModelPart namedPart = entityMappingType.findByPath( targetPartName );
		if ( namedPart == null ) {
			// This is expected to happen when processing a
			// PostInitCallbackEntry because the callbacks
			// are not ordered. The exception is caught in
			// MappingModelCreationProcess.executePostInitCallbacks()
			// and the callback is re-queued.
			throw new IllegalStateException(
					String.format(
							Locale.ROOT,
							"Could not resolve path `%s` relative to `%s` for many-to-many foreign-key target mapping - `%s`",
							targetPartName,
							entityMappingType.getEntityName(),
							collectionDescriptor.getRole()
					)
			);
		}
		return (ValuedModelPart) namedPart;
	}

	private ForeignKeyDescriptor createForeignKeyDescriptor(
			Value fkBootDescriptorSource,
			EntityType entityType,
			ModelPart fkTargetModelPart,
			MappingModelCreationProcess creationProcess,
			Dialect dialect) {
		assert fkTargetModelPart != null;

		// If this is mapped by a to-one attribute, we can use the FK of that attribute
		if ( fkTargetModelPart instanceof ToOneAttributeMapping ) {
			final ToOneAttributeMapping toOneAttributeMapping = (ToOneAttributeMapping) fkTargetModelPart;
			if ( toOneAttributeMapping.getForeignKeyDescriptor() == null ) {
				throw new IllegalStateException( "Not yet ready: " + toOneAttributeMapping );
			}
			return toOneAttributeMapping.getForeignKeyDescriptor();
		}

		if ( fkTargetModelPart instanceof ManyToManyCollectionPart ) {
			// can this ever be anything other than another (the inverse) many-to-many part?
			final ManyToManyCollectionPart targetModelPart = (ManyToManyCollectionPart) fkTargetModelPart;
			if ( targetModelPart.getForeignKeyDescriptor() == null ) {
				throw new IllegalStateException( "Not yet ready: " + targetModelPart );
			}
			return targetModelPart.getForeignKeyDescriptor();
		}

		final String collectionTableName = ( (CollectionMutationTarget) getCollectionDescriptor() ).getCollectionTableMapping().getTableName();

		if ( fkTargetModelPart instanceof BasicValuedModelPart ) {
			return createSimpleForeignKeyDescriptor(
					fkBootDescriptorSource,
					entityType,
					creationProcess,
					dialect,
					collectionTableName,
					(BasicValuedModelPart) fkTargetModelPart
			);
		}

		if ( fkTargetModelPart instanceof EmbeddableValuedModelPart ) {
			return MappingModelCreationHelper.buildEmbeddableForeignKeyDescriptor(
					(EmbeddableValuedModelPart) fkTargetModelPart,
					fkBootDescriptorSource,
					findContainingEntityMapping(),
					getCollectionDescriptor().getAttributeMapping(),
					false,
					fkBootDescriptorSource.getColumnInsertability(),
					fkBootDescriptorSource.getColumnUpdateability(),
					dialect,
					creationProcess
			);
		}

		throw new UnsupportedOperationException(
				"Could not create many-to-many foreign-key : " + getNavigableRole().getFullPath()
		);
	}

	private SimpleForeignKeyDescriptor createSimpleForeignKeyDescriptor(
			Value fkBootDescriptorSource,
			EntityType entityType,
			MappingModelCreationProcess creationProcess,
			Dialect dialect,
			String fkKeyTableName,
			BasicValuedModelPart basicFkTargetPart) {
		final boolean columnInsertable;
		final boolean columnUpdateable;
		if ( getNature() == Nature.ELEMENT ) {
			// Replicate behavior of AbstractCollectionPersister#elementColumnIsSettable
			columnInsertable = true;
			columnUpdateable = true;
		}
		else {
			columnInsertable = fkBootDescriptorSource.isColumnInsertable( 0 );
			columnUpdateable = fkBootDescriptorSource.isColumnUpdateable( 0 );
		}
		final SelectableMapping keySelectableMapping = SelectableMappingImpl.from(
				fkKeyTableName,
				fkBootDescriptorSource.getSelectables().get(0),
				basicFkTargetPart.getJdbcMapping(),
				creationProcess.getCreationContext().getTypeConfiguration(),
				columnInsertable,
				columnUpdateable,
				((SimpleValue) fkBootDescriptorSource).isPartitionKey(),
				dialect,
				creationProcess.getSqmFunctionRegistry()
		);

		final boolean hasConstraint;
		if ( fkBootDescriptorSource instanceof SimpleValue ) {
			hasConstraint = ( (SimpleValue) fkBootDescriptorSource ).isConstrained();
		}
		else {
			// We assume there is a constraint if the key is not nullable
			hasConstraint = !fkBootDescriptorSource.isNullable();
		}

		// here we build a ModelPart that represents the many-to-many table key referring to the element table
		return new SimpleForeignKeyDescriptor(
				getAssociatedEntityMappingType(),
				keySelectableMapping,
				basicFkTargetPart,
				entityType.isReferenceToPrimaryKey(),
				hasConstraint
		);
	}
}
