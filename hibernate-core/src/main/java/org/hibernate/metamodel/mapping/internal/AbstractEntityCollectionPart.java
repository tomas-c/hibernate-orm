/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.metamodel.mapping.internal;

import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.NotFoundAction;
import org.hibernate.engine.FetchStyle;
import org.hibernate.engine.FetchTiming;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.IndexedCollection;
import org.hibernate.mapping.OneToMany;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.ToOne;
import org.hibernate.mapping.Value;
import org.hibernate.metamodel.mapping.AssociationKey;
import org.hibernate.metamodel.mapping.EntityIdentifierMapping;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.ForeignKeyDescriptor;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.metamodel.model.domain.NavigableRole;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.PropertyMapping;
import org.hibernate.spi.NavigablePath;
import org.hibernate.sql.ast.spi.FromClauseAccess;
import org.hibernate.sql.ast.spi.SqlAliasBase;
import org.hibernate.sql.ast.spi.SqlAstCreationContext;
import org.hibernate.sql.ast.spi.SqlAstCreationState;
import org.hibernate.sql.ast.spi.SqlExpressionResolver;
import org.hibernate.sql.ast.tree.from.PluralTableGroup;
import org.hibernate.sql.ast.tree.from.StandardTableGroup;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableGroupProducer;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.FetchOptions;
import org.hibernate.sql.results.graph.FetchParent;
import org.hibernate.sql.results.graph.collection.internal.EagerCollectionFetch;
import org.hibernate.sql.results.graph.entity.EntityFetch;
import org.hibernate.sql.results.graph.entity.internal.EntityFetchJoinedImpl;
import org.hibernate.type.CompositeType;
import org.hibernate.type.Type;

/**
 * Base support for EntityCollectionPart implementations
 *
 * @author Steve Ebersole
 */
public abstract class AbstractEntityCollectionPart implements EntityCollectionPart, FetchOptions, TableGroupProducer {
	private final NavigableRole navigableRole;
	private final Nature nature;
	private final CollectionPersister collectionDescriptor;
	private final EntityMappingType associatedEntityTypeDescriptor;
	private final NotFoundAction notFoundAction;

	private final Set<String> targetKeyPropertyNames;

	public AbstractEntityCollectionPart(
			Nature nature,
			Collection collectionBootDescriptor,
			CollectionPersister collectionDescriptor,
			EntityMappingType associatedEntityTypeDescriptor,
			NotFoundAction notFoundAction,
			MappingModelCreationProcess creationProcess) {
		this.navigableRole = collectionDescriptor.getNavigableRole().appendContainer( nature.getName() );
		this.nature = nature;
		this.collectionDescriptor = collectionDescriptor;
		this.associatedEntityTypeDescriptor = associatedEntityTypeDescriptor;
		this.notFoundAction = notFoundAction;

		this.targetKeyPropertyNames = resolveTargetKeyPropertyNames(
				nature,
				collectionDescriptor,
				collectionBootDescriptor,
				associatedEntityTypeDescriptor,
				creationProcess
		);
	}

	/**
	 * For Hibernate Reactive
	 */
	protected AbstractEntityCollectionPart(AbstractEntityCollectionPart original) {
		this.navigableRole = original.navigableRole;
		this.nature = original.nature;
		this.collectionDescriptor = original.collectionDescriptor;
		this.associatedEntityTypeDescriptor = original.associatedEntityTypeDescriptor;
		this.notFoundAction = original.notFoundAction;
		this.targetKeyPropertyNames = original.targetKeyPropertyNames;
	}

	@Override
	public String toString() {
		return "EntityCollectionPart(" + navigableRole.getFullPath() + ")@" + System.identityHashCode( this );
	}

	public CollectionPersister getCollectionDescriptor() {
		return collectionDescriptor;
	}

	@Override
	public EntityMappingType getMappedType() {
		return getAssociatedEntityMappingType();
	}

	protected Set<String> getTargetKeyPropertyNames() {
		return targetKeyPropertyNames;
	}

	@Override
	public NavigableRole getNavigableRole() {
		return navigableRole;
	}

	@Override
	public Nature getNature() {
		return nature;
	}

	@Override
	public String getFetchableName() {
		return nature.getName();
	}

	@Override
	public int getFetchableKey() {
		return nature == Nature.INDEX || !collectionDescriptor.hasIndex() ? 0 : 1;
	}

	@Override
	public EntityMappingType getAssociatedEntityMappingType() {
		return associatedEntityTypeDescriptor;
	}

	@Override
	public NotFoundAction getNotFoundAction() {
		return notFoundAction;
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
	public boolean incrementFetchDepth() {
		// the collection itself already increments the depth
		return false;
	}

	@Override
	public boolean isOptional() {
		return false;
	}

	@Override
	public boolean isUnwrapProxy() {
		return false;
	}

	@Override
	public EntityMappingType findContainingEntityMapping() {
		return collectionDescriptor.getAttributeMapping().findContainingEntityMapping();
	}

	@Override
	public int getNumberOfFetchables() {
		return getAssociatedEntityMappingType().getNumberOfFetchables();
	}

	@Override
	public <T> DomainResult<T> createDomainResult(
			NavigablePath navigablePath,
			TableGroup tableGroup,
			String resultVariable,
			DomainResultCreationState creationState) {
		final TableGroup partTableGroup = resolveTableGroup( navigablePath, creationState );
		return associatedEntityTypeDescriptor.createDomainResult( navigablePath, partTableGroup, resultVariable, creationState );
	}

	@Override
	public EntityFetch generateFetch(
			FetchParent fetchParent,
			NavigablePath fetchablePath,
			FetchTiming fetchTiming,
			boolean selected,
			String resultVariable,
			DomainResultCreationState creationState) {
		final AssociationKey associationKey = resolveFetchAssociationKey();
		final boolean added = creationState.registerVisitedAssociationKey( associationKey );

		final TableGroup partTableGroup = resolveTableGroup( fetchablePath, creationState );
		final EntityFetch fetch = buildEntityFetchJoined(
				fetchParent,
				this,
				partTableGroup,
				fetchablePath,
				creationState
		);

		if ( added ) {
			creationState.removeVisitedAssociationKey( associationKey );
		}

		return fetch;
	}

	/**
	 * For Hibernate Reactive
	 */
	protected EagerCollectionFetch buildEagerCollectionFetch(
			NavigablePath fetchedPath,
			PluralAttributeMapping fetchedAttribute,
			TableGroup collectionTableGroup,
			FetchParent fetchParent,
			DomainResultCreationState creationState) {
		return new EagerCollectionFetch(
				fetchedPath,
				fetchedAttribute,
				collectionTableGroup,
				fetchParent,
				creationState
		);
	}

	/**
	 * For Hibernate Reactive
	 */
	protected EntityFetch buildEntityFetchJoined(
			FetchParent fetchParent,
			AbstractEntityCollectionPart abstractEntityCollectionPart,
			TableGroup partTableGroup,
			NavigablePath fetchablePath,
			DomainResultCreationState creationState) {
		return new EntityFetchJoinedImpl(
				fetchParent,
				abstractEntityCollectionPart,
				partTableGroup,
				fetchablePath,
				creationState
		);
	}

	protected abstract AssociationKey resolveFetchAssociationKey();

	private TableGroup resolveTableGroup(NavigablePath fetchablePath, DomainResultCreationState creationState) {
		final FromClauseAccess fromClauseAccess = creationState.getSqlAstCreationState().getFromClauseAccess();
		return fromClauseAccess.resolveTableGroup( fetchablePath, (np) -> {
			final PluralTableGroup parentTableGroup = (PluralTableGroup) fromClauseAccess.getTableGroup( np.getParent() );
			switch ( nature ) {
				case ELEMENT: {
					return parentTableGroup.getElementTableGroup();
				}
				case INDEX: {
					return resolveIndexTableGroup( parentTableGroup, fetchablePath, fromClauseAccess, creationState );
				}
			}

			throw new IllegalStateException( "Could not find table group for: " + np );
		} );
	}

	private TableGroup resolveIndexTableGroup(
			PluralTableGroup collectionTableGroup,
			NavigablePath fetchablePath,
			FromClauseAccess fromClauseAccess,
			DomainResultCreationState creationState) {
		return collectionTableGroup.getIndexTableGroup();
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// TableGroupProducer

	@Override
	public String getSqlAliasStem() {
		return getCollectionDescriptor().getAttributeMapping().getSqlAliasStem();
	}

	@Override
	public boolean containsTableReference(String tableExpression) {
		return getCollectionDescriptor().getAttributeMapping().containsTableReference( tableExpression );
	}

	public TableGroup createTableGroupInternal(
			boolean canUseInnerJoins,
			NavigablePath navigablePath,
			boolean fetched,
			String sourceAlias,
			final SqlAliasBase sqlAliasBase,
			SqlAstCreationState creationState) {
		final SqlAstCreationContext creationContext = creationState.getCreationContext();
		final SqlExpressionResolver sqlExpressionResolver = creationState.getSqlExpressionResolver();
		final TableReference primaryTableReference = getEntityMappingType().createPrimaryTableReference(
				sqlAliasBase,
				creationState
		);

		return new StandardTableGroup(
				canUseInnerJoins,
				navigablePath,
				this,
				fetched,
				sourceAlias,
				primaryTableReference,
				true,
				sqlAliasBase,
				(tableExpression) -> getEntityMappingType().containsTableReference( tableExpression ),
				(tableExpression, tg) -> getEntityMappingType().createTableReferenceJoin(
						tableExpression,
						sqlAliasBase,
						primaryTableReference,
						creationState
				),
				creationContext.getSessionFactory()
		);
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Initialization

	private static Set<String> resolveTargetKeyPropertyNames(
			Nature nature,
			CollectionPersister collectionDescriptor,
			Collection collectionBootDescriptor,
			EntityMappingType elementTypeDescriptor,
			MappingModelCreationProcess creationProcess) {
		final Value bootModelValue = nature == Nature.INDEX
				? ( (IndexedCollection) collectionBootDescriptor ).getIndex()
				: collectionBootDescriptor.getElement();
		final PersistentClass entityBinding = creationProcess.getCreationContext()
				.getMetadata()
				.getEntityBinding( elementTypeDescriptor.getEntityName() );

		final String referencedPropertyName;
		if ( bootModelValue instanceof OneToMany ) {
			final String mappedByProperty = collectionDescriptor.getMappedByProperty();
			referencedPropertyName = StringHelper.isEmpty( mappedByProperty )
					? null
					: mappedByProperty;
		}
		else {
			final ToOne toOne = (ToOne) bootModelValue;
			referencedPropertyName = toOne.getReferencedPropertyName();
		}


		if ( referencedPropertyName == null ) {
			final Set<String> targetKeyPropertyNames = new HashSet<>( 2 );
			targetKeyPropertyNames.add( EntityIdentifierMapping.ROLE_LOCAL_NAME );
			final Type propertyType;
			if ( entityBinding.getIdentifierMapper() == null ) {
				propertyType = entityBinding.getIdentifier().getType();
			}
			else {
				propertyType = entityBinding.getIdentifierMapper().getType();
			}
			if ( entityBinding.getIdentifierProperty() == null ) {
				final CompositeType compositeType;
				if ( propertyType.isComponentType() && ( compositeType = (CompositeType) propertyType ).isEmbedded()
						&& compositeType.getPropertyNames().length == 1 ) {
					ToOneAttributeMapping.addPrefixedPropertyNames(
							targetKeyPropertyNames,
							compositeType.getPropertyNames()[0],
							compositeType.getSubtypes()[0],
							creationProcess.getCreationContext().getSessionFactory()
					);
					ToOneAttributeMapping.addPrefixedPropertyNames(
							targetKeyPropertyNames,
							ForeignKeyDescriptor.PART_NAME,
							compositeType.getSubtypes()[0],
							creationProcess.getCreationContext().getSessionFactory()
					);
				}
				else {
					ToOneAttributeMapping.addPrefixedPropertyNames(
							targetKeyPropertyNames,
							null,
							propertyType,
							creationProcess.getCreationContext().getSessionFactory()
					);
					ToOneAttributeMapping.addPrefixedPropertyNames(
							targetKeyPropertyNames,
							ForeignKeyDescriptor.PART_NAME,
							propertyType,
							creationProcess.getCreationContext().getSessionFactory()
					);
				}
			}
			else {
				ToOneAttributeMapping.addPrefixedPropertyNames(
						targetKeyPropertyNames,
						entityBinding.getIdentifierProperty().getName(),
						propertyType,
						creationProcess.getCreationContext().getSessionFactory()
				);
				ToOneAttributeMapping.addPrefixedPropertyNames(
						targetKeyPropertyNames,
						ForeignKeyDescriptor.PART_NAME,
						propertyType,
						creationProcess.getCreationContext().getSessionFactory()
				);
			}
			return targetKeyPropertyNames;
		}
		else if ( bootModelValue instanceof OneToMany ) {
			final Set<String> targetKeyPropertyNames = new HashSet<>( 2 );
			int dotIndex = -1;
			while ( ( dotIndex = referencedPropertyName.indexOf( '.', dotIndex + 1 ) ) != -1 ) {
				targetKeyPropertyNames.add( referencedPropertyName.substring( 0, dotIndex ) );
			}
			// todo (PropertyMapping) : the problem here is timing.  this needs to be delayed.
			final Type propertyType = ( (PropertyMapping) elementTypeDescriptor.getEntityPersister() )
					.toType( referencedPropertyName );
			ToOneAttributeMapping.addPrefixedPropertyNames(
					targetKeyPropertyNames,
					referencedPropertyName,
					propertyType,
					creationProcess.getCreationContext().getSessionFactory()
			);
			ToOneAttributeMapping.addPrefixedPropertyNames(
					targetKeyPropertyNames,
					ForeignKeyDescriptor.PART_NAME,
					propertyType,
					creationProcess.getCreationContext().getSessionFactory()
			);
			return targetKeyPropertyNames;
		}
		else {
			final Type propertyType = entityBinding.getRecursiveProperty( referencedPropertyName ).getType();
			final CompositeType compositeType;
			if ( propertyType.isComponentType() && ( compositeType = (CompositeType) propertyType ).isEmbedded()
					&& compositeType.getPropertyNames().length == 1 ) {
				final Set<String> targetKeyPropertyNames = new HashSet<>( 2 );
				ToOneAttributeMapping.addPrefixedPropertyNames(
						targetKeyPropertyNames,
						compositeType.getPropertyNames()[0],
						compositeType.getSubtypes()[0],
						creationProcess.getCreationContext().getSessionFactory()
				);
				ToOneAttributeMapping.addPrefixedPropertyNames(
						targetKeyPropertyNames,
						ForeignKeyDescriptor.PART_NAME,
						compositeType.getSubtypes()[0],
						creationProcess.getCreationContext().getSessionFactory()
				);
				return targetKeyPropertyNames;
			}
			else {
				final String mapsIdAttributeName;
				if ( ( mapsIdAttributeName = ToOneAttributeMapping.findMapsIdPropertyName( elementTypeDescriptor, referencedPropertyName ) ) != null ) {
					final Set<String> targetKeyPropertyNames = new HashSet<>( 2 );
					targetKeyPropertyNames.add( referencedPropertyName );
					ToOneAttributeMapping.addPrefixedPropertyNames(
							targetKeyPropertyNames,
							mapsIdAttributeName,
							elementTypeDescriptor.getEntityPersister().getIdentifierType(),
							creationProcess.getCreationContext().getSessionFactory()
					);
					ToOneAttributeMapping.addPrefixedPropertyNames(
							targetKeyPropertyNames,
							ForeignKeyDescriptor.PART_NAME,
							elementTypeDescriptor.getEntityPersister().getIdentifierType(),
							creationProcess.getCreationContext().getSessionFactory()
					);
					return targetKeyPropertyNames;
				}
				else {
					return Set.of( referencedPropertyName, ForeignKeyDescriptor.PART_NAME );
				}
			}
		}
	}
}
