/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.boot.model.internal;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.hibernate.AnnotationException;
import org.hibernate.AssertionFailure;
import org.hibernate.MappingException;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Checks;
import org.hibernate.annotations.DiscriminatorFormula;
import org.hibernate.annotations.DiscriminatorOptions;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Filters;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.HQLSelect;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Loader;
import org.hibernate.annotations.Mutability;
import org.hibernate.annotations.NaturalIdCache;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OptimisticLockType;
import org.hibernate.annotations.OptimisticLocking;
import org.hibernate.annotations.Persister;
import org.hibernate.annotations.Polymorphism;
import org.hibernate.annotations.PolymorphismType;
import org.hibernate.annotations.Proxy;
import org.hibernate.annotations.RowId;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLDeleteAll;
import org.hibernate.annotations.SQLDeletes;
import org.hibernate.annotations.SQLInsert;
import org.hibernate.annotations.SQLInserts;
import org.hibernate.annotations.SQLSelect;
import org.hibernate.annotations.SQLUpdate;
import org.hibernate.annotations.SQLUpdates;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.SecondaryRow;
import org.hibernate.annotations.SecondaryRows;
import org.hibernate.annotations.SelectBeforeUpdate;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;
import org.hibernate.annotations.Tables;
import org.hibernate.annotations.TypeBinderType;
import org.hibernate.annotations.Where;
import org.hibernate.annotations.common.reflection.ReflectionManager;
import org.hibernate.annotations.common.reflection.XAnnotatedElement;
import org.hibernate.annotations.common.reflection.XClass;
import org.hibernate.binder.TypeBinder;
import org.hibernate.boot.model.IdentifierGeneratorDefinition;
import org.hibernate.boot.model.NamedEntityGraphDefinition;
import org.hibernate.boot.model.internal.InheritanceState.ElementsToProcess;
import org.hibernate.boot.model.naming.EntityNaming;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.ImplicitEntityNameSource;
import org.hibernate.boot.model.naming.NamingStrategyHelper;
import org.hibernate.boot.model.relational.QualifiedTableName;
import org.hibernate.boot.spi.AccessType;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.boot.spi.PropertyData;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.OptimisticLockStyle;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.engine.spi.FilterDefinition;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.jpa.event.spi.CallbackType;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.CheckConstraint;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.Join;
import org.hibernate.mapping.JoinedSubclass;
import org.hibernate.mapping.MappedSuperclass;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.SingleTableSubclass;
import org.hibernate.mapping.Subclass;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.TableOwner;
import org.hibernate.mapping.UnionSubclass;
import org.hibernate.mapping.Value;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.spi.NavigablePath;

import org.jboss.logging.Logger;

import jakarta.persistence.Access;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Cacheable;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.NamedEntityGraphs;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.PrimaryKeyJoinColumns;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.SecondaryTables;
import jakarta.persistence.SharedCacheMode;
import jakarta.persistence.UniqueConstraint;

import static org.hibernate.boot.model.internal.AnnotatedClassType.MAPPED_SUPERCLASS;
import static org.hibernate.boot.model.internal.AnnotatedDiscriminatorColumn.buildDiscriminatorColumn;
import static org.hibernate.boot.model.internal.AnnotatedJoinColumn.buildInheritanceJoinColumn;
import static org.hibernate.boot.model.internal.BinderHelper.getMappedSuperclassOrNull;
import static org.hibernate.boot.model.internal.BinderHelper.getOverridableAnnotation;
import static org.hibernate.boot.model.internal.BinderHelper.hasToOneAnnotation;
import static org.hibernate.boot.model.internal.BinderHelper.isDefault;
import static org.hibernate.boot.model.internal.BinderHelper.toAliasEntityMap;
import static org.hibernate.boot.model.internal.BinderHelper.toAliasTableMap;
import static org.hibernate.boot.model.internal.EmbeddableBinder.fillEmbeddable;
import static org.hibernate.boot.model.internal.GeneratorBinder.makeIdGenerator;
import static org.hibernate.boot.model.internal.HCANNHelper.findContainingAnnotations;
import static org.hibernate.boot.model.internal.InheritanceState.getInheritanceStateOfSuperEntity;
import static org.hibernate.boot.model.internal.PropertyBinder.addElementsOfClass;
import static org.hibernate.boot.model.internal.PropertyBinder.processElementAnnotations;
import static org.hibernate.boot.model.internal.PropertyHolderBuilder.buildPropertyHolder;
import static org.hibernate.engine.spi.ExecuteUpdateResultCheckStyle.fromResultCheckStyle;
import static org.hibernate.internal.util.StringHelper.isEmpty;
import static org.hibernate.internal.util.StringHelper.isNotEmpty;
import static org.hibernate.internal.util.StringHelper.nullIfEmpty;
import static org.hibernate.internal.util.StringHelper.unqualify;
import static org.hibernate.jpa.event.internal.CallbackDefinitionResolverLegacyImpl.resolveEmbeddableCallbacks;
import static org.hibernate.jpa.event.internal.CallbackDefinitionResolverLegacyImpl.resolveEntityCallbacks;
import static org.hibernate.mapping.SimpleValue.DEFAULT_ID_GEN_STRATEGY;


/**
 * Stateful binder responsible for interpreting information about an {@link Entity} class
 * and producing a {@link PersistentClass} mapping model object.
 *
 * @author Emmanuel Bernard
 */
public class EntityBinder {

	private static final CoreMessageLogger LOG = Logger.getMessageLogger( CoreMessageLogger.class, EntityBinder.class.getName() );
	private static final String NATURAL_ID_CACHE_SUFFIX = "##NaturalId";

	private MetadataBuildingContext context;

	private String name;
	private XClass annotatedClass;
	private PersistentClass persistentClass;
	private Boolean forceDiscriminator;
	private Boolean insertableDiscriminator;
	private PolymorphismType polymorphismType;
	private boolean lazy;
	private XClass proxyClass;
	private String where;
	// todo : we should defer to InFlightMetadataCollector.EntityTableXref for secondary table tracking;
	//		atm we use both from here; HBM binding solely uses InFlightMetadataCollector.EntityTableXref
	private final java.util.Map<String, Join> secondaryTables = new HashMap<>();
	private final java.util.Map<String, Object> secondaryTableJoins = new HashMap<>();
	private final java.util.Map<String, Join> secondaryTablesFromAnnotation = new HashMap<>();
	private final java.util.Map<String, Object> secondaryTableFromAnnotationJoins = new HashMap<>();

	private final List<Filter> filters = new ArrayList<>();
	private boolean ignoreIdAnnotations;
	private AccessType propertyAccessType = AccessType.DEFAULT;
	private boolean wrapIdsInEmbeddedComponents;
	private String subselect;

	private boolean isCached;
	private String cacheConcurrentStrategy;
	private String cacheRegion;
	private boolean cacheLazyProperty;
	private String naturalIdCacheRegion;

	/**
	 * Bind an entity class. This can be done in a single pass.
	 */
	public static void bindEntityClass(
			XClass clazzToProcess,
			Map<XClass, InheritanceState> inheritanceStates,
			Map<String, IdentifierGeneratorDefinition> generators,
			MetadataBuildingContext context) {
		if ( LOG.isDebugEnabled() ) {
			LOG.debugf( "Binding entity from annotated class: %s", clazzToProcess.getName() );
		}

		//TODO: be more strict with secondary table allowance (not for ids, not for secondary table join columns etc)

		final InheritanceState inheritanceState = inheritanceStates.get( clazzToProcess );
		final PersistentClass superEntity = getSuperEntity( clazzToProcess, inheritanceStates, context, inheritanceState );
		detectedAttributeOverrideProblem( clazzToProcess, superEntity );
		final PersistentClass persistentClass = makePersistentClass( inheritanceState, superEntity, context );
		final EntityBinder entityBinder = new EntityBinder( clazzToProcess, persistentClass, context );
		entityBinder.bindEntity();
		entityBinder.handleClassTable( inheritanceState, superEntity );
		entityBinder.handleSecondaryTables();
		entityBinder.handleCheckConstraints();
		final PropertyHolder holder = buildPropertyHolder(
				clazzToProcess,
				persistentClass,
				entityBinder,
				context,
				inheritanceStates
		);
		entityBinder.handleInheritance( inheritanceState, superEntity, holder );
		entityBinder.handleIdentifier( holder, inheritanceStates, generators, inheritanceState );

		final InFlightMetadataCollector collector = context.getMetadataCollector();
		if ( persistentClass instanceof RootClass ) {
			collector.addSecondPass( new CreateKeySecondPass( (RootClass) persistentClass ) );
		}
		if ( persistentClass instanceof Subclass) {
			assert superEntity != null;
			superEntity.addSubclass( (Subclass) persistentClass );
		}
		collector.addEntityBinding( persistentClass );
		// process secondary tables and complementary definitions (ie o.h.a.Table)
		collector.addSecondPass( new SecondaryTableFromAnnotationSecondPass( entityBinder, holder, clazzToProcess ) );
		collector.addSecondPass( new SecondaryTableSecondPass( entityBinder, holder, clazzToProcess ) );
		// comment, checkConstraint, and indexes are processed here
		entityBinder.processComplementaryTableDefinitions();
		bindCallbacks( clazzToProcess, persistentClass, context );
		entityBinder.callTypeBinders( persistentClass );
	}

	private void handleCheckConstraints() {
		if ( annotatedClass.isAnnotationPresent( Checks.class ) ) {
			// if we have more than one of them they are not overrideable :-/
			for ( Check check : annotatedClass.getAnnotation( Checks.class ).value() ) {
				addCheckToEntity( check );
			}
		}
		else {
			final Check check = getOverridableAnnotation( annotatedClass, Check.class, context );
			if ( check != null ) {
				addCheckToEntity( check );
			}
		}
	}

	/**
	 * For now, we store it on the entity.
	 * Later we will come back and figure out which table it belongs to.
	 */
	private void addCheckToEntity(Check check) {
		final String name = check.name();
		final String constraint = check.constraints();
		persistentClass.addCheckConstraint( name.isEmpty()
				? new CheckConstraint( constraint )
				: new CheckConstraint( name, constraint ) );
	}

	private void callTypeBinders(PersistentClass persistentClass) {
		for ( Annotation containingAnnotation : findContainingAnnotations( annotatedClass, TypeBinderType.class ) ) {
			final TypeBinderType binderType = containingAnnotation.annotationType().getAnnotation( TypeBinderType.class );
			try {
				final TypeBinder binder = binderType.binder().newInstance();
				binder.bind( containingAnnotation, context, persistentClass );
			}
			catch ( Exception e ) {
				throw new AnnotationException( "error processing @TypeBinderType annotation '" + containingAnnotation + "'", e );
			}
		}
	}

	private void handleIdentifier(
			PropertyHolder propertyHolder,
			Map<XClass, InheritanceState> inheritanceStates,
			Map<String, IdentifierGeneratorDefinition> generators,
			InheritanceState inheritanceState) {
		final ElementsToProcess elementsToProcess = inheritanceState.postProcess( persistentClass, this );
		final Set<String> idPropertiesIfIdClass = handleIdClass(
				persistentClass,
				inheritanceState,
				context,
				propertyHolder,
				elementsToProcess,
				inheritanceStates
		);
		processIdPropertiesIfNotAlready(
				persistentClass,
				inheritanceState,
				context,
				propertyHolder,
				generators,
				idPropertiesIfIdClass,
				elementsToProcess,
				inheritanceStates
		);
	}

	private void processComplementaryTableDefinitions() {
		processComplementaryTableDefinitions( annotatedClass.getAnnotation( org.hibernate.annotations.Table.class ) );
		processComplementaryTableDefinitions( annotatedClass.getAnnotation( org.hibernate.annotations.Tables.class ) );
		processComplementaryTableDefinitions( annotatedClass.getAnnotation( jakarta.persistence.Table.class ) );
	}

	private static void detectedAttributeOverrideProblem(XClass clazzToProcess, PersistentClass superEntity) {
		if ( superEntity != null && (
				clazzToProcess.isAnnotationPresent( AttributeOverride.class ) ||
						clazzToProcess.isAnnotationPresent( AttributeOverrides.class ) ) ) {
			LOG.unsupportedAttributeOverrideWithEntityInheritance( clazzToProcess.getName() );
		}
	}

	private Set<String> handleIdClass(
			PersistentClass persistentClass,
			InheritanceState inheritanceState,
			MetadataBuildingContext context,
			PropertyHolder propertyHolder,
			ElementsToProcess elementsToProcess,
			Map<XClass, InheritanceState> inheritanceStates) {
		final Set<String> idPropertiesIfIdClass = new HashSet<>();
		boolean isIdClass = mapAsIdClass(
				inheritanceStates,
				inheritanceState,
				persistentClass,
				propertyHolder,
				elementsToProcess,
				idPropertiesIfIdClass,
				context
		);
		if ( !isIdClass ) {
			setWrapIdsInEmbeddedComponents( elementsToProcess.getIdPropertyCount() > 1 );
		}
		return idPropertiesIfIdClass;
	}

	private boolean mapAsIdClass(
			Map<XClass, InheritanceState> inheritanceStates,
			InheritanceState inheritanceState,
			PersistentClass persistentClass,
			PropertyHolder propertyHolder,
			ElementsToProcess elementsToProcess,
			Set<String> idPropertiesIfIdClass,
			MetadataBuildingContext context) {

		// We are looking for @IdClass
		// In general we map the id class as identifier using the mapping metadata of the main entity's
		// properties and we create an identifier mapper containing the id properties of the main entity
		final XClass classWithIdClass = inheritanceState.getClassWithIdClass( false );
		if ( classWithIdClass != null ) {
			final Class<?> idClassValue = classWithIdClass.getAnnotation( IdClass.class ).value();
			final XClass compositeClass = context.getBootstrapContext().getReflectionManager().toXClass( idClassValue );
			final AccessType accessType = getPropertyAccessType();
			final PropertyData inferredData = new PropertyPreloadedData( accessType, "id", compositeClass );
			final PropertyData baseInferredData = new PropertyPreloadedData( accessType, "id", classWithIdClass );
			final AccessType propertyAccessor = getPropertyAccessor( compositeClass );

			// In JPA 2, there is a shortcut if the IdClass is the Pk of the associated class pointed to by the id
			// it ought to be treated as an embedded and not a real IdClass (at least in Hibernate's internal way)
			final boolean isFakeIdClass = isIdClassPkOfTheAssociatedEntity(
					elementsToProcess,
					compositeClass,
					inferredData,
					baseInferredData,
					propertyAccessor,
					inheritanceStates,
					context
			);

			if ( isFakeIdClass ) {
				return false;
			}
			else {
				final boolean ignoreIdAnnotations = isIgnoreIdAnnotations();
				setIgnoreIdAnnotations( true );
				bindIdClass(
						inferredData,
						baseInferredData,
						propertyHolder,
						propertyAccessor,
						context,
						inheritanceStates
				);
				final Component mapper = createMapperProperty(
						inheritanceStates,
						persistentClass,
						propertyHolder,
						context,
						classWithIdClass,
						compositeClass,
						baseInferredData,
						propertyAccessor
				);
				setIgnoreIdAnnotations( ignoreIdAnnotations );
				for ( Property property : mapper.getProperties() ) {
					idPropertiesIfIdClass.add( property.getName() );
				}
				return true;
			}
		}
		else {
			return false;
		}
	}

	private Component createMapperProperty(
			Map<XClass, InheritanceState> inheritanceStates,
			PersistentClass persistentClass,
			PropertyHolder propertyHolder,
			MetadataBuildingContext context,
			XClass classWithIdClass,
			XClass compositeClass,
			PropertyData baseInferredData,
			AccessType propertyAccessor) {
		final Component mapper = createMapper(
				inheritanceStates,
				persistentClass,
				propertyHolder,
				context,
				classWithIdClass,
				compositeClass,
				baseInferredData,
				propertyAccessor
		);
		final Property mapperProperty = new Property();
		mapperProperty.setName( NavigablePath.IDENTIFIER_MAPPER_PROPERTY );
		mapperProperty.setUpdateable( false );
		mapperProperty.setInsertable( false );
		mapperProperty.setPropertyAccessorName( "embedded" );
		mapperProperty.setValue( mapper );
		persistentClass.addProperty( mapperProperty);
		return mapper;
	}

	private Component createMapper(
			Map<XClass, InheritanceState> inheritanceStates,
			PersistentClass persistentClass,
			PropertyHolder propertyHolder,
			MetadataBuildingContext context,
			XClass classWithIdClass,
			XClass compositeClass,
			PropertyData baseInferredData,
			AccessType propertyAccessor) {
		final Component mapper = fillEmbeddable(
				propertyHolder,
				new PropertyPreloadedData(
						propertyAccessor,
						NavigablePath.IDENTIFIER_MAPPER_PROPERTY,
						compositeClass
				),
				baseInferredData,
				propertyAccessor,
				false,
				this,
				true,
				true,
				false,
				null,
				null,
				null,
				context,
				inheritanceStates
		);
		persistentClass.setIdentifierMapper( mapper );

		// If id definition is on a mapped superclass, update the mapping
		final MappedSuperclass superclass = getMappedSuperclassOrNull( classWithIdClass, inheritanceStates, context );
		if ( superclass != null ) {
			superclass.setDeclaredIdentifierMapper( mapper );
		}
		else {
			// we are for sure on the entity
			persistentClass.setDeclaredIdentifierMapper( mapper );
		}
		return mapper;
	}

	private static PropertyData getUniqueIdPropertyFromBaseClass(
			PropertyData inferredData,
			PropertyData baseInferredData,
			AccessType propertyAccessor,
			MetadataBuildingContext context) {
		final List<PropertyData> baseClassElements = new ArrayList<>();
		final PropertyContainer propContainer = new PropertyContainer(
				baseInferredData.getClassOrElement(),
				inferredData.getPropertyClass(),
				propertyAccessor
		);
		addElementsOfClass( baseClassElements, propContainer, context );
		//Id properties are on top and there is only one
		return baseClassElements.get( 0 );
	}

	private static boolean isIdClassPkOfTheAssociatedEntity(
			ElementsToProcess elementsToProcess,
			XClass compositeClass,
			PropertyData inferredData,
			PropertyData baseInferredData,
			AccessType propertyAccessor,
			Map<XClass, InheritanceState> inheritanceStates,
			MetadataBuildingContext context) {
		if ( elementsToProcess.getIdPropertyCount() == 1 ) {
			final PropertyData idPropertyOnBaseClass = getUniqueIdPropertyFromBaseClass(
					inferredData,
					baseInferredData,
					propertyAccessor,
					context
			);
			final InheritanceState state = inheritanceStates.get( idPropertyOnBaseClass.getClassOrElement() );
			if ( state == null ) {
				return false; //while it is likely a user error, let's consider it is something that might happen
			}
			final XClass associatedClassWithIdClass = state.getClassWithIdClass( true );
			if ( associatedClassWithIdClass == null ) {
				//we cannot know for sure here unless we try and find the @EmbeddedId
				//Let's not do this thorough checking but do some extra validation
				return hasToOneAnnotation( idPropertyOnBaseClass.getProperty() );

			}
			else {
				final IdClass idClass = associatedClassWithIdClass.getAnnotation(IdClass.class);
				//noinspection unchecked
				return context.getBootstrapContext().getReflectionManager().toXClass( idClass.value() )
						.equals( compositeClass );
			}
		}
		else {
			return false;
		}
	}

	private void bindIdClass(
			PropertyData inferredData,
			PropertyData baseInferredData,
			PropertyHolder propertyHolder,
			AccessType propertyAccessor,
			MetadataBuildingContext buildingContext,
			Map<XClass, InheritanceState> inheritanceStates) {
		propertyHolder.setInIdClass( true );

		// Fill simple value and property since and Id is a property
		final PersistentClass persistentClass = propertyHolder.getPersistentClass();
		if ( !(persistentClass instanceof RootClass) ) {
			throw new AnnotationException( "Entity '" + persistentClass.getEntityName()
					+ "' is a subclass in an entity inheritance hierarchy and may not redefine the identifier of the root entity" );
		}
		final RootClass rootClass = (RootClass) persistentClass;
		final Component id = fillEmbeddable(
				propertyHolder,
				inferredData,
				baseInferredData,
				propertyAccessor,
				false,
				this,
				true,
				false,
				false,
				null,
				null,
				null,
				buildingContext,
				inheritanceStates
		);
		id.setKey( true );
		if ( rootClass.getIdentifier() != null ) {
			throw new AssertionFailure( "Entity '" + persistentClass.getEntityName()
					+ "' has an '@IdClass' and may not have an identifier property" );
		}
		if ( id.getPropertySpan() == 0 ) {
			throw new AnnotationException( "Class '" + id.getComponentClassName()
					+ " is the '@IdClass' for the entity '" + persistentClass.getEntityName()
					+ "' but has no persistent properties" );
		}

		rootClass.setIdentifier( id );

		handleIdGenerator( inferredData, buildingContext, id );

		rootClass.setEmbeddedIdentifier( inferredData.getPropertyClass() == null );

		propertyHolder.setInIdClass( null );
	}

	private static void handleIdGenerator(PropertyData inferredData, MetadataBuildingContext buildingContext, Component id) {
		if ( buildingContext.getBootstrapContext().getJpaCompliance().isGlobalGeneratorScopeEnabled() ) {
			buildingContext.getMetadataCollector().addSecondPass( new IdGeneratorResolverSecondPass(
					id,
					inferredData.getProperty(),
					DEFAULT_ID_GEN_STRATEGY,
					"",
					buildingContext
			) );
		}
		else {
			makeIdGenerator(
					id,
					inferredData.getProperty(),
					DEFAULT_ID_GEN_STRATEGY,
					"",
					buildingContext,
					Collections.emptyMap()
			);
		}
	}

	private void handleSecondaryTables() {
		final SecondaryTable secTable = annotatedClass.getAnnotation( SecondaryTable.class );
		final SecondaryTables secTables = annotatedClass.getAnnotation( SecondaryTables.class );
		if ( secTables != null ) {
			//loop through it
			for ( SecondaryTable tab : secTables.value() ) {
				addJoin( tab, null, false );
			}
		}
		else if ( secTable != null ) {
			addJoin( secTable, null, false );
		}
	}

	private void handleClassTable(InheritanceState inheritanceState, PersistentClass superEntity) {
		final String schema;
		final String table;
		final String catalog;
		final List<UniqueConstraintHolder> uniqueConstraints;
		boolean hasTableAnnotation = annotatedClass.isAnnotationPresent( jakarta.persistence.Table.class );
		if ( hasTableAnnotation ) {
			final jakarta.persistence.Table tableAnnotation = annotatedClass.getAnnotation( jakarta.persistence.Table.class );
			table = tableAnnotation.name();
			schema = tableAnnotation.schema();
			catalog = tableAnnotation.catalog();
			uniqueConstraints = TableBinder.buildUniqueConstraintHolders( tableAnnotation.uniqueConstraints() );
		}
		else {
			//might be no @Table annotation on the annotated class
			schema = "";
			table = "";
			catalog = "";
			uniqueConstraints = Collections.emptyList();
		}

		final InFlightMetadataCollector collector = context.getMetadataCollector();
		if ( inheritanceState.hasTable() ) {
			createTable( inheritanceState, superEntity, schema, table, catalog, uniqueConstraints, collector );
		}
		else {
			if ( hasTableAnnotation ) {
				//TODO: why is this not an error?!
				LOG.invalidTableAnnotation( annotatedClass.getName() );
			}

			if ( inheritanceState.getType() == InheritanceType.SINGLE_TABLE ) {
				// we at least need to properly set up the EntityTableXref
				bindTableForDiscriminatedSubclass( collector.getEntityTableXref( superEntity.getEntityName() ) );
			}
		}
	}

	private void createTable(
			InheritanceState inheritanceState,
			PersistentClass superEntity,
			String schema,
			String table,
			String catalog,
			List<UniqueConstraintHolder> uniqueConstraints,
			InFlightMetadataCollector collector) {
		final RowId rowId = annotatedClass.getAnnotation( RowId.class );
		bindTable(
				schema,
				catalog,
				table,
				uniqueConstraints,
				rowId == null ? null : rowId.value(),
				inheritanceState.hasDenormalizedTable()
						? collector.getEntityTableXref( superEntity.getEntityName() )
						: null
		);
	}

	private void handleInheritance(
			InheritanceState inheritanceState,
			PersistentClass superEntity,
			PropertyHolder propertyHolder) {
		final boolean isJoinedSubclass;
		switch ( inheritanceState.getType() ) {
			case JOINED:
				joinedInheritance( inheritanceState, superEntity, propertyHolder );
				isJoinedSubclass = inheritanceState.hasParents();
				break;
			case SINGLE_TABLE:
				singleTableInheritance( inheritanceState, propertyHolder );
				isJoinedSubclass = false;
				break;
			default:
				isJoinedSubclass = false;
		}

		bindDiscriminatorValue();

		if ( !isJoinedSubclass ) {
			checkNoJoinColumns( annotatedClass );
			if ( annotatedClass.isAnnotationPresent( OnDelete.class ) ) {
				//TODO: why is this not an error!??
				LOG.invalidOnDeleteAnnotation( propertyHolder.getEntityName() );
			}
		}
	}

	private void singleTableInheritance(InheritanceState inheritanceState, PropertyHolder holder) {
		processDiscriminatorOptions();
		final AnnotatedDiscriminatorColumn discriminatorColumn = processSingleTableDiscriminatorProperties( inheritanceState );
		if ( !inheritanceState.hasParents() ) { // todo : sucks that this is separate from RootClass distinction
			final RootClass rootClass = (RootClass) persistentClass;
			if ( inheritanceState.hasSiblings()
					|| discriminatorColumn != null && !discriminatorColumn.isImplicit() ) {
				bindDiscriminatorColumnToRootPersistentClass( rootClass, discriminatorColumn, holder );
				rootClass.setForceDiscriminator( isForceDiscriminatorInSelects() );
			}
		}
	}

	private void joinedInheritance(InheritanceState state, PersistentClass superEntity, PropertyHolder holder) {
		processDiscriminatorOptions();

		if ( state.hasParents() ) {
			final AnnotatedJoinColumns joinColumns = subclassJoinColumns( annotatedClass, superEntity, context );
			final JoinedSubclass jsc = (JoinedSubclass) persistentClass;
			final DependantValue key = new DependantValue( context, jsc.getTable(), jsc.getIdentifier() );
			jsc.setKey( key );
			handleForeignKeys( annotatedClass, context, key );
			final OnDelete onDelete = annotatedClass.getAnnotation( OnDelete.class );
			key.setOnDeleteAction( onDelete == null ? null : onDelete.action() );
			//we are never in a second pass at that stage, so queue it
			context.getMetadataCollector()
					.addSecondPass( new JoinedSubclassFkSecondPass( jsc, joinColumns, key, context) );
			context.getMetadataCollector()
					.addSecondPass( new CreateKeySecondPass( jsc ) );
		}

		final AnnotatedDiscriminatorColumn discriminatorColumn = processJoinedDiscriminatorProperties( state );
		if ( !state.hasParents() ) {  // todo : sucks that this is separate from RootClass distinction
			final RootClass rootClass = (RootClass) persistentClass;
			// the class we're processing is the root of the hierarchy, so
			// let's see if we had a discriminator column (it's perfectly
			// valid for joined inheritance to not have a discriminator)
			if ( discriminatorColumn == null ) {
				if ( isForceDiscriminatorInSelects() ) {
					throw new AnnotationException( "Entity '" + rootClass.getEntityName()
							+ "' with 'JOINED' inheritance is annotated '@DiscriminatorOptions(force=true)' but has no discriminator column" );
				}
			}
			else {
				// we do have a discriminator column
				if ( state.hasSiblings() || !discriminatorColumn.isImplicit() ) {
					bindDiscriminatorColumnToRootPersistentClass( rootClass, discriminatorColumn, holder );
					rootClass.setForceDiscriminator( isForceDiscriminatorInSelects() );
				}
			}
		}
	}

	private static void checkNoJoinColumns(XClass clazzToProcess) {
		if ( clazzToProcess.isAnnotationPresent( PrimaryKeyJoinColumns.class )
				|| clazzToProcess.isAnnotationPresent( PrimaryKeyJoinColumn.class ) ) {
			//TODO: why is this not an error?!
			LOG.invalidPrimaryKeyJoinColumnAnnotation( clazzToProcess.getName() );
		}
	}

	private static void handleForeignKeys(XClass clazzToProcess, MetadataBuildingContext context, DependantValue key) {
		final ForeignKey foreignKey = clazzToProcess.getAnnotation( ForeignKey.class );
		if ( foreignKey != null && !foreignKey.name().isEmpty() ) {
			key.setForeignKeyName( foreignKey.name() );
		}
		else {
			final PrimaryKeyJoinColumn pkJoinColumn = clazzToProcess.getAnnotation( PrimaryKeyJoinColumn.class );
			final PrimaryKeyJoinColumns pkJoinColumns = clazzToProcess.getAnnotation( PrimaryKeyJoinColumns.class );
			final boolean noConstraintByDefault = context.getBuildingOptions().isNoConstraintByDefault();
			if ( pkJoinColumns != null && ( pkJoinColumns.foreignKey().value() == ConstraintMode.NO_CONSTRAINT
					|| pkJoinColumns.foreignKey().value() == ConstraintMode.PROVIDER_DEFAULT && noConstraintByDefault ) ) {
				// don't apply a constraint based on ConstraintMode
				key.disableForeignKey();
			}
			else if ( pkJoinColumns != null && isNotEmpty( pkJoinColumns.foreignKey().name() ) ) {
				key.setForeignKeyName( pkJoinColumns.foreignKey().name() );
				if ( !pkJoinColumns.foreignKey().foreignKeyDefinition().isEmpty() ) {
					key.setForeignKeyDefinition( pkJoinColumns.foreignKey().foreignKeyDefinition() );
				}
			}
			else if ( pkJoinColumn != null && ( pkJoinColumn.foreignKey().value() == ConstraintMode.NO_CONSTRAINT
					|| pkJoinColumn.foreignKey().value() == ConstraintMode.PROVIDER_DEFAULT && noConstraintByDefault ) ) {
				// don't apply a constraint based on ConstraintMode
				key.disableForeignKey();
			}
			else if ( pkJoinColumn != null && isNotEmpty( pkJoinColumn.foreignKey().name() ) ) {
				key.setForeignKeyName( pkJoinColumn.foreignKey().name() );
				if ( !pkJoinColumn.foreignKey().foreignKeyDefinition().isEmpty() ) {
					key.setForeignKeyDefinition( pkJoinColumn.foreignKey().foreignKeyDefinition() );
				}
			}
		}
	}

	private void bindDiscriminatorColumnToRootPersistentClass(
			RootClass rootClass,
			AnnotatedDiscriminatorColumn discriminatorColumn,
			PropertyHolder holder) {
		if ( rootClass.getDiscriminator() == null ) {
			if ( discriminatorColumn == null ) {
				throw new AssertionFailure( "discriminator column should have been built" );
			}
			final AnnotatedColumns columns = new AnnotatedColumns();
			columns.setPropertyHolder( holder );
			columns.setBuildingContext( context );
			columns.setJoins( secondaryTables );
//			discriminatorColumn.setJoins( secondaryTables );
//			discriminatorColumn.setPropertyHolder( holder );
			discriminatorColumn.setParent( columns );

			final BasicValue discriminatorColumnBinding = new BasicValue( context, rootClass.getTable() );
			rootClass.setDiscriminator( discriminatorColumnBinding );
			discriminatorColumn.linkWithValue( discriminatorColumnBinding );
			discriminatorColumnBinding.setTypeName( discriminatorColumn.getDiscriminatorTypeName() );
			rootClass.setPolymorphic( true );
			if ( LOG.isTraceEnabled() ) {
				LOG.tracev( "Setting discriminator for entity {0}", rootClass.getEntityName() );
			}
			context.getMetadataCollector().addSecondPass(
					new NullableDiscriminatorColumnSecondPass( rootClass.getEntityName() )
			);
		}
		if ( insertableDiscriminator != null ) {
			rootClass.setDiscriminatorInsertable( insertableDiscriminator );
		}
	}

	private void processDiscriminatorOptions() {
		final DiscriminatorOptions discriminatorOptions = annotatedClass.getAnnotation( DiscriminatorOptions.class );
		if ( discriminatorOptions != null ) {
			forceDiscriminator = discriminatorOptions.force();
			insertableDiscriminator = discriminatorOptions.insert();
		}
	}

	/**
	 * Process all discriminator-related metadata per rules for "single table" inheritance
	 */
	private AnnotatedDiscriminatorColumn processSingleTableDiscriminatorProperties(InheritanceState inheritanceState) {

		final DiscriminatorColumn discriminatorColumn = annotatedClass.getAnnotation( DiscriminatorColumn.class );

		final DiscriminatorFormula discriminatorFormula =
				getOverridableAnnotation( annotatedClass, DiscriminatorFormula.class, context );

		if ( !inheritanceState.hasParents() || annotatedClass.isAnnotationPresent( Inheritance.class ) ) {
			return buildDiscriminatorColumn( discriminatorColumn, discriminatorFormula, context );
		}
		else {
			// not a root entity
			if ( discriminatorColumn != null ) {
				throw new AnnotationException( "Entity class '" + annotatedClass.getName()
						+  "' is annotated '@DiscriminatorColumn' but it is not the root of the entity inheritance hierarchy");
			}
			if ( discriminatorFormula != null ) {
				throw new AnnotationException( "Entity class '" + annotatedClass.getName()
						+  "' is annotated '@DiscriminatorFormula' but it is not the root of the entity inheritance hierarchy");
			}
			return null;
		}
	}

	/**
	 * Process all discriminator-related metadata per rules for "joined" inheritance, taking
	 * into account {@value AvailableSettings#IMPLICIT_DISCRIMINATOR_COLUMNS_FOR_JOINED_SUBCLASS}
	 * and {@value AvailableSettings#IGNORE_EXPLICIT_DISCRIMINATOR_COLUMNS_FOR_JOINED_SUBCLASS}.
	 */
	private AnnotatedDiscriminatorColumn processJoinedDiscriminatorProperties(InheritanceState inheritanceState) {
		if ( annotatedClass.isAnnotationPresent( DiscriminatorFormula.class ) ) {
			throw new AnnotationException( "Entity class '" + annotatedClass.getName()
					+  "' has 'JOINED' inheritance and is annotated '@DiscriminatorFormula'" );
		}

		final DiscriminatorColumn discriminatorColumn = annotatedClass.getAnnotation( DiscriminatorColumn.class );
		if ( !inheritanceState.hasParents() || annotatedClass.isAnnotationPresent( Inheritance.class ) ) {
			return useDiscriminatorColumnForJoined( discriminatorColumn )
					? buildDiscriminatorColumn( discriminatorColumn, null, context )
					: null;
		}
		else {
			// not a root entity
			if ( discriminatorColumn != null ) {
				throw new AnnotationException( "Entity class '" + annotatedClass.getName()
						+  "' is annotated '@DiscriminatorColumn' but it is not the root of the entity inheritance hierarchy");
			}
			return null;
		}
	}

	/**
	 * We want to process the discriminator column if either:
	 * <ol>
	 * <li>there is an explicit {@link DiscriminatorColumn} annotation and we are not told to ignore it
	 *     via {@value AvailableSettings#IGNORE_EXPLICIT_DISCRIMINATOR_COLUMNS_FOR_JOINED_SUBCLASS}, or
	 * <li>there is no explicit {@link DiscriminatorColumn} annotation but we are told to create it
	 *     implicitly via {@value AvailableSettings#IMPLICIT_DISCRIMINATOR_COLUMNS_FOR_JOINED_SUBCLASS}.
	 * </ol>
	 */
	private boolean useDiscriminatorColumnForJoined(DiscriminatorColumn discriminatorColumn) {
		if ( discriminatorColumn != null ) {
			boolean ignore = context.getBuildingOptions().ignoreExplicitDiscriminatorsForJoinedInheritance();
			if ( ignore ) {
				LOG.debugf( "Ignoring explicit @DiscriminatorColumn annotation on: %s", annotatedClass.getName() );
			}
			else {
				LOG.applyingExplicitDiscriminatorColumnForJoined(
						annotatedClass.getName(),
						AvailableSettings.IGNORE_EXPLICIT_DISCRIMINATOR_COLUMNS_FOR_JOINED_SUBCLASS
				);
			}
			return !ignore;
		}
		else {
			boolean createImplicit = context.getBuildingOptions().createImplicitDiscriminatorsForJoinedInheritance();
			if ( createImplicit ) {
				LOG.debugf( "Inferring implicit @DiscriminatorColumn using defaults for: %s", annotatedClass.getName() );
			}
			return createImplicit;
		}
	}

	private void processIdPropertiesIfNotAlready(
			PersistentClass persistentClass,
			InheritanceState inheritanceState,
			MetadataBuildingContext context,
			PropertyHolder propertyHolder,
			Map<String, IdentifierGeneratorDefinition> generators,
			Set<String> idPropertiesIfIdClass,
			ElementsToProcess elementsToProcess,
			Map<XClass, InheritanceState> inheritanceStates) {

		final Set<String> missingIdProperties = new HashSet<>( idPropertiesIfIdClass );
		for ( PropertyData propertyAnnotatedElement : elementsToProcess.getElements() ) {
			final String propertyName = propertyAnnotatedElement.getPropertyName();
			if ( !idPropertiesIfIdClass.contains( propertyName ) ) {
				boolean subclassAndSingleTableStrategy =
						inheritanceState.getType() == InheritanceType.SINGLE_TABLE
								&& inheritanceState.hasParents();
				processElementAnnotations(
						propertyHolder,
						subclassAndSingleTableStrategy
								? Nullability.FORCED_NULL
								: Nullability.NO_CONSTRAINT,
						propertyAnnotatedElement,
						generators,
						this,
						false,
						false,
						false,
						context,
						inheritanceStates
				);
			}
			else {
				missingIdProperties.remove( propertyName );
			}
		}

		if ( missingIdProperties.size() != 0 ) {
			final StringBuilder missings = new StringBuilder();
			for ( String property : missingIdProperties ) {
				if ( missings.length() > 0 ) {
					missings.append(", ");
				}
				missings.append("'").append( property ).append( "'" );
			}
			throw new AnnotationException( "Entity '" + persistentClass.getEntityName()
					+ "' has an '@IdClass' with properties " + missings
					+ " which do not match properties of the entity class" );
		}
	}

	private static PersistentClass makePersistentClass(
			InheritanceState inheritanceState,
			PersistentClass superEntity,
			MetadataBuildingContext metadataBuildingContext) {
		//we now know what kind of persistent entity it is
		if ( !inheritanceState.hasParents() ) {
			return new RootClass( metadataBuildingContext );
		}
		else {
			switch ( inheritanceState.getType() ) {
				case SINGLE_TABLE:
					return new SingleTableSubclass( superEntity, metadataBuildingContext );
				case JOINED:
					return new JoinedSubclass( superEntity, metadataBuildingContext );
				case TABLE_PER_CLASS:
					return new UnionSubclass( superEntity, metadataBuildingContext );
				default:
					throw new AssertionFailure( "Unknown inheritance type: " + inheritanceState.getType() );
			}
		}
	}

	private static AnnotatedJoinColumns subclassJoinColumns(
			XClass clazzToProcess,
			PersistentClass superEntity,
			MetadataBuildingContext context) {
		//@Inheritance(JOINED) subclass need to link back to the super entity
		final AnnotatedJoinColumns joinColumns = new AnnotatedJoinColumns();
		joinColumns.setBuildingContext( context );
		final PrimaryKeyJoinColumns primaryKeyJoinColumns = clazzToProcess.getAnnotation( PrimaryKeyJoinColumns.class );
		if ( primaryKeyJoinColumns != null ) {
			final PrimaryKeyJoinColumn[] columns = primaryKeyJoinColumns.value();
			if ( columns.length > 0 ) {
				for ( PrimaryKeyJoinColumn column : columns ) {
					buildInheritanceJoinColumn(
							column,
							null,
							superEntity.getIdentifier(),
							joinColumns,
							context
					);
				}
			}
			else {
				buildInheritanceJoinColumn(
						clazzToProcess.getAnnotation( PrimaryKeyJoinColumn.class ),
						null,
						superEntity.getIdentifier(),
						joinColumns,
						context
				);
			}
		}
		else {
			buildInheritanceJoinColumn(
					clazzToProcess.getAnnotation( PrimaryKeyJoinColumn.class ),
					null,
					superEntity.getIdentifier(),
					joinColumns,
					context
			);
		}
		LOG.trace( "Subclass joined column(s) created" );
		return joinColumns;
	}

	private static PersistentClass getSuperEntity(
			XClass clazzToProcess,
			Map<XClass, InheritanceState> inheritanceStates,
			MetadataBuildingContext context,
			InheritanceState inheritanceState) {
		final InheritanceState superState = getInheritanceStateOfSuperEntity( clazzToProcess, inheritanceStates );
		if ( superState == null ) {
			return null;
		}
		else {
			final PersistentClass superEntity = context.getMetadataCollector()
					.getEntityBinding( superState.getClazz().getName() );
			//check if superclass is not a potential persistent class
			if ( superEntity == null && inheritanceState.hasParents() ) {
				throw new AssertionFailure( "Subclass has to be bound after its parent class: "
						+ superState.getClazz().getName() );
			}
			return superEntity;
		}
	}

	private static void bindCallbacks(XClass entityClass, PersistentClass persistentClass, MetadataBuildingContext context) {
		final ReflectionManager reflection = context.getBootstrapContext().getReflectionManager();
		for ( CallbackType callbackType : CallbackType.values() ) {
			persistentClass.addCallbackDefinitions( resolveEntityCallbacks( reflection, entityClass, callbackType ) );
		}
		context.getMetadataCollector().addSecondPass( persistentClasses -> {
			for ( Property property : persistentClass.getDeclaredProperties() ) {
				final Class<?> mappedClass = persistentClass.getMappedClass();
				if ( property.isComposite() ) {
					for ( CallbackType type : CallbackType.values() ) {
						property.addCallbackDefinitions( resolveEmbeddableCallbacks( reflection, mappedClass, property, type ) );
					}
				}
			}
		} );
	}

	public boolean wrapIdsInEmbeddedComponents() {
		return wrapIdsInEmbeddedComponents;
	}

	/**
	 * Use as a fake one for Collection of elements
	 */
	public EntityBinder() {
	}

	public EntityBinder(XClass annotatedClass, PersistentClass persistentClass, MetadataBuildingContext context) {
		this.context = context;
		this.persistentClass = persistentClass;
		this.annotatedClass = annotatedClass;
	}

	/**
	 * Delegates to {@link PersistentClass#isPropertyDefinedInHierarchy},
	 * after verifying that there is a {@link PersistentClass} available.
	 *
	 * @param name The name of the property to check
	 *
	 * @return {@code true} if a property by that given name does already exist in the super hierarchy.
	 */
	public boolean isPropertyDefinedInSuperHierarchy(String name) {
		// Yes, yes... persistentClass can be null because EntityBinder
		// can be used to bind Components (naturally!)
		return persistentClass != null
			&& persistentClass.isPropertyDefinedInSuperHierarchy( name );
	}

	private void bindRowManagement() {
		final DynamicInsert dynamicInsertAnn = annotatedClass.getAnnotation( DynamicInsert.class );
		persistentClass.setDynamicInsert( dynamicInsertAnn != null && dynamicInsertAnn.value() );
		final DynamicUpdate dynamicUpdateAnn = annotatedClass.getAnnotation( DynamicUpdate.class );
		persistentClass.setDynamicUpdate( dynamicUpdateAnn != null && dynamicUpdateAnn.value() );
		final SelectBeforeUpdate selectBeforeUpdateAnn = annotatedClass.getAnnotation( SelectBeforeUpdate.class );
		persistentClass.setSelectBeforeUpdate( selectBeforeUpdateAnn != null && selectBeforeUpdateAnn.value() );
	}

	private void bindOptimisticLocking() {
		final OptimisticLocking optimisticLockingAnn = annotatedClass.getAnnotation( OptimisticLocking.class );
		persistentClass.setOptimisticLockStyle(
				getVersioning( optimisticLockingAnn == null ? OptimisticLockType.VERSION : optimisticLockingAnn.type() )
		);
	}

	private void bindPolymorphism() {
		final Polymorphism polymorphismAnn = annotatedClass.getAnnotation( Polymorphism.class );
		polymorphismType = polymorphismAnn == null ? PolymorphismType.IMPLICIT : polymorphismAnn.type();
	}

	private void bindEntityAnnotation() {
		final Entity entity = annotatedClass.getAnnotation( Entity.class );
		if ( entity == null ) {
			throw new AssertionFailure( "@Entity should never be missing" );
		}
		final String entityName = entity.name();
		name = entityName.isEmpty() ? unqualify( annotatedClass.getName() ) : entityName;
	}

	public boolean isRootEntity() {
		// This is the best option I can think of here since
		// PersistentClass is most likely not yet fully populated
		return persistentClass instanceof RootClass;
	}

	public void bindEntity() {
		bindEntityAnnotation();
		bindRowManagement();
		bindOptimisticLocking();
		bindPolymorphism();
		bindProxy();
		bindBatchSize();
		bindWhere();
		bindCache();
		bindNaturalIdCache();
		bindFiltersInHierarchy();

		persistentClass.setAbstract( annotatedClass.isAbstract() );
		persistentClass.setClassName( annotatedClass.getName() );
		persistentClass.setJpaEntityName( name );
		persistentClass.setEntityName( annotatedClass.getName() );
		persistentClass.setCached( isCached );
		persistentClass.setLazy( lazy );
		if ( proxyClass != null ) {
			persistentClass.setProxyInterfaceName( proxyClass.getName() );
		}

		if ( persistentClass instanceof RootClass ) {
			bindRootEntity();
		}
		else if ( !isMutable() ) {
			LOG.immutableAnnotationOnNonRoot( annotatedClass.getName() );
		}

		ensureNoMutabilityPlan();

		bindCustomPersister();
		bindCustomSql();
		bindSynchronize();
		bindFilters();

		registerImportName();

		processNamedEntityGraphs();
	}

	private void ensureNoMutabilityPlan() {
		if ( annotatedClass.isAnnotationPresent( Mutability.class ) ) {
			throw new MappingException( "@Mutability is not allowed on entity" );
		}
	}

	private boolean isMutable() {
		return !annotatedClass.isAnnotationPresent(Immutable.class);
	}

	private void registerImportName() {
		LOG.debugf( "Import with entity name %s", name );
		try {
			context.getMetadataCollector().addImport( name, persistentClass.getEntityName() );
			final String entityName = persistentClass.getEntityName();
			if ( !entityName.equals( name ) ) {
				context.getMetadataCollector().addImport( entityName, entityName );
			}
		}
		catch (MappingException me) {
			throw new AnnotationException( "Use of the same entity name twice: " + name, me );
		}
	}

	private void bindRootEntity() {
		final RootClass rootClass = (RootClass) persistentClass;
		rootClass.setMutable( isMutable() );
		rootClass.setExplicitPolymorphism( isExplicitPolymorphism( polymorphismType ) );
		if ( isNotEmpty( where ) ) {
			rootClass.setWhere( where );
		}
		if ( cacheConcurrentStrategy != null ) {
			rootClass.setCacheConcurrencyStrategy( cacheConcurrentStrategy );
			rootClass.setCacheRegionName( cacheRegion );
			rootClass.setLazyPropertiesCacheable( cacheLazyProperty );
		}
		rootClass.setNaturalIdCacheRegionName( naturalIdCacheRegion );
	}

	private boolean isForceDiscriminatorInSelects() {
		return forceDiscriminator == null
				? context.getBuildingOptions().shouldImplicitlyForceDiscriminatorInSelect()
				: forceDiscriminator;
	}

	private void bindCustomSql() {
		//TODO: tolerate non-empty table() member here if it explicitly names the main table
		//TODO: would be nice to add these guys to @DialectOverride, but getOverridableAnnotation()
		//      does not yet handle repeatable annotations

		final SQLInsert sqlInsert = findMatchingSqlAnnotation( "", SQLInsert.class, SQLInserts.class );
		if ( sqlInsert != null ) {
			persistentClass.setCustomSQLInsert(
					sqlInsert.sql().trim(),
					sqlInsert.callable(),
					fromResultCheckStyle( sqlInsert.check() )
			);

		}

		final SQLUpdate sqlUpdate = findMatchingSqlAnnotation( "", SQLUpdate.class, SQLUpdates.class );
		if ( sqlUpdate != null ) {
			persistentClass.setCustomSQLUpdate(
					sqlUpdate.sql().trim(),
					sqlUpdate.callable(),
					fromResultCheckStyle( sqlUpdate.check() )
			);
		}

		final SQLDelete sqlDelete = findMatchingSqlAnnotation( "", SQLDelete.class, SQLDeletes.class );
		if ( sqlDelete != null ) {
			persistentClass.setCustomSQLDelete(
					sqlDelete.sql().trim(),
					sqlDelete.callable(),
					fromResultCheckStyle( sqlDelete.check() )
			);
		}

		final SQLDeleteAll sqlDeleteAll = annotatedClass.getAnnotation( SQLDeleteAll.class );
		if ( sqlDeleteAll != null ) {
			throw new AnnotationException("@SQLDeleteAll does not apply to entities: "
					+ persistentClass.getEntityName());
		}

		final SQLSelect sqlSelect = getOverridableAnnotation( annotatedClass, SQLSelect.class, context );
		if ( sqlSelect != null ) {
			final String loaderName = persistentClass.getEntityName() + "$SQLSelect";
			persistentClass.setLoaderName( loaderName );
			QueryBinder.bindNativeQuery( loaderName, sqlSelect, annotatedClass, context );
		}

		final HQLSelect hqlSelect = annotatedClass.getAnnotation( HQLSelect.class );
		if ( hqlSelect != null ) {
			final String loaderName = persistentClass.getEntityName() + "$HQLSelect";
			persistentClass.setLoaderName( loaderName );
			QueryBinder.bindQuery( loaderName, hqlSelect, context );
		}

		final Loader loader = annotatedClass.getAnnotation( Loader.class );
		if ( loader != null ) {
			persistentClass.setLoaderName( loader.namedQuery() );
		}

		final Subselect subselect = annotatedClass.getAnnotation( Subselect.class );
		if ( subselect != null ) {
			this.subselect = subselect.value();
		}
	}

	private void bindFilters() {
		for ( Filter filter : filters ) {
			String condition = filter.condition();
			if ( condition.isEmpty() ) {
				condition = getDefaultFilterCondition( filter.name() );
			}
			persistentClass.addFilter(
					filter.name(),
					condition,
					filter.deduceAliasInjectionPoints(),
					toAliasTableMap( filter.aliases() ),
					toAliasEntityMap( filter.aliases() )
			);
		}
	}

	private String getDefaultFilterCondition(String filterName) {
		final FilterDefinition definition = context.getMetadataCollector().getFilterDefinition( filterName );
		if ( definition == null ) {
			throw new AnnotationException( "Entity '" + name
					+ "' has a '@Filter' for an undefined filter named '" + filterName + "'" );
		}
		final String condition = definition.getDefaultFilterCondition();
		if ( isEmpty( condition ) ) {
			throw new AnnotationException( "Entity '" + name +
					"' has a '@Filter' with no 'condition' and no default condition was given by the '@FilterDef' named '"
					+ filterName + "'" );
		}
		return condition;
	}

	private void bindSynchronize() {
		if ( annotatedClass.isAnnotationPresent( Synchronize.class ) ) {
			final JdbcEnvironment jdbcEnvironment = context.getMetadataCollector().getDatabase().getJdbcEnvironment();
			final Synchronize synchronize = annotatedClass.getAnnotation(Synchronize.class);
			for ( String table : synchronize.value() ) {
				String physicalName = synchronize.logical() ? toPhysicalName( jdbcEnvironment, table ) : table;
				persistentClass.addSynchronizedTable( physicalName );
			}
		}
	}

	private String toPhysicalName(JdbcEnvironment jdbcEnvironment, String logicalName) {
		return context.getBuildingOptions().getPhysicalNamingStrategy()
				.toPhysicalTableName(
						jdbcEnvironment.getIdentifierHelper().toIdentifier( logicalName ),
						jdbcEnvironment
				)
				.render( jdbcEnvironment.getDialect() );
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void bindCustomPersister() {
		//set persister if needed
		final Persister persisterAnn = annotatedClass.getAnnotation( Persister.class );
		if ( persisterAnn != null ) {
			Class clazz = persisterAnn.impl();
			if ( !EntityPersister.class.isAssignableFrom(clazz) ) {
				throw new AnnotationException( "Persister class '" + clazz.getName()
						+ "' does not implement 'EntityPersister'" );
			}
			persistentClass.setEntityPersisterClass( clazz );
		}
	}

	public PersistentClass getPersistentClass() {
		return persistentClass;
	}

	private void processNamedEntityGraphs() {
		processNamedEntityGraph( annotatedClass.getAnnotation( NamedEntityGraph.class ) );
		final NamedEntityGraphs graphs = annotatedClass.getAnnotation( NamedEntityGraphs.class );
		if ( graphs != null ) {
			for ( NamedEntityGraph graph : graphs.value() ) {
				processNamedEntityGraph( graph );
			}
		}
	}

	private void processNamedEntityGraph(NamedEntityGraph annotation) {
		if ( annotation == null ) {
			return;
		}
		context.getMetadataCollector().addNamedEntityGraph(
				new NamedEntityGraphDefinition( annotation, name, persistentClass.getEntityName() )
		);
	}

	public void bindDiscriminatorValue() {
		final String discriminatorValue = annotatedClass.isAnnotationPresent( DiscriminatorValue.class )
				? annotatedClass.getAnnotation( DiscriminatorValue.class ).value()
				: null;
		if ( isEmpty( discriminatorValue ) ) {
			final Value discriminator = persistentClass.getDiscriminator();
			if ( discriminator == null ) {
				persistentClass.setDiscriminatorValue( name );
			}
			else if ( "character".equals( discriminator.getType().getName() ) ) {
				throw new AnnotationException( "Entity '" + name
						+ "' has a discriminator of character type and must specify its '@DiscriminatorValue'" );
			}
			else if ( "integer".equals( discriminator.getType().getName() ) ) {
				persistentClass.setDiscriminatorValue( String.valueOf( name.hashCode() ) );
			}
			else {
				persistentClass.setDiscriminatorValue( name ); //Spec compliant
			}
		}
		else {
			persistentClass.setDiscriminatorValue( discriminatorValue );
		}
	}

	private OptimisticLockStyle getVersioning(OptimisticLockType type) {
		switch ( type ) {
			case VERSION:
				return OptimisticLockStyle.VERSION;
			case NONE:
				return OptimisticLockStyle.NONE;
			case DIRTY:
				return OptimisticLockStyle.DIRTY;
			case ALL:
				return OptimisticLockStyle.ALL;
			default:
				throw new AssertionFailure( "optimistic locking not supported: " + type );
		}
	}

	private boolean isExplicitPolymorphism(PolymorphismType type) {
		switch ( type ) {
			case IMPLICIT:
				return false;
			case EXPLICIT:
				return true;
			default:
				throw new AssertionFailure( "Unknown polymorphism type: " + type );
		}
	}

	public void bindBatchSize() {
		final BatchSize batchSize = annotatedClass.getAnnotation( BatchSize.class );
		persistentClass.setBatchSize(batchSize != null ? batchSize.size() : -1 );
	}

	public void bindProxy() {
		final Proxy proxy = annotatedClass.getAnnotation( Proxy.class );
		if ( proxy != null ) {
			lazy = proxy.lazy();
			if ( !lazy ) {
				proxyClass = null;
			}
			else {
				final ReflectionManager reflectionManager = context.getBootstrapContext().getReflectionManager();
				proxyClass = isDefault( reflectionManager.toXClass(proxy.proxyClass() ), context )
						? annotatedClass
						: reflectionManager.toXClass(proxy.proxyClass());
			}
		}
		else {
			lazy = true; //needed to allow association lazy loading.
			proxyClass = annotatedClass;
		}
	}

	public void bindWhere() {
		final Where where = getOverridableAnnotation( annotatedClass, Where.class, context );
		if ( where != null ) {
			this.where = where.clause();
		}
		final SQLRestriction restriction = getOverridableAnnotation( annotatedClass, SQLRestriction.class, context );
		if ( restriction != null ) {
			this.where = restriction.value();
		}
	}

	public void setWrapIdsInEmbeddedComponents(boolean wrapIdsInEmbeddedComponents) {
		this.wrapIdsInEmbeddedComponents = wrapIdsInEmbeddedComponents;
	}

	private void bindNaturalIdCache() {
		naturalIdCacheRegion = null;
		final NaturalIdCache naturalIdCacheAnn = annotatedClass.getAnnotation( NaturalIdCache.class );
		if ( naturalIdCacheAnn != null ) {
			if ( naturalIdCacheAnn.region().isEmpty() ) {
				final Cache explicitCacheAnn = annotatedClass.getAnnotation( Cache.class );
				naturalIdCacheRegion = explicitCacheAnn != null && isNotEmpty( explicitCacheAnn.region() )
						? explicitCacheAnn.region() + NATURAL_ID_CACHE_SUFFIX
						: annotatedClass.getName() + NATURAL_ID_CACHE_SUFFIX;
			}
			else {
				naturalIdCacheRegion = naturalIdCacheAnn.region();
			}
		}
	}

	private void bindCache() {
		isCached = false;
		cacheConcurrentStrategy = null;
		cacheRegion = null;
		cacheLazyProperty = true;
		final SharedCacheMode sharedCacheMode  = context.getBuildingOptions().getSharedCacheMode();
		if ( persistentClass instanceof RootClass ) {
			bindRootClassCache( sharedCacheMode, context );
		}
		else {
			bindSubclassCache( sharedCacheMode );
		}
	}

	private void bindSubclassCache(SharedCacheMode sharedCacheMode) {
		final Cache cache = annotatedClass.getAnnotation( Cache.class );
		final Cacheable cacheable = annotatedClass.getAnnotation( Cacheable.class );
		if ( cache != null ) {
			LOG.cacheOrCacheableAnnotationOnNonRoot(
					persistentClass.getClassName() == null
							? annotatedClass.getName()
							: persistentClass.getClassName()
			);
		}
		else if ( cacheable == null && persistentClass.getSuperclass() != null ) {
			// we should inherit our super's caching config
			isCached = persistentClass.getSuperclass().isCached();
		}
		else {
			isCached = isCacheable( sharedCacheMode, cacheable );
		}
	}

	private void bindRootClassCache(SharedCacheMode sharedCacheMode, MetadataBuildingContext context) {
		final Cache cache = annotatedClass.getAnnotation( Cache.class );
		final Cacheable cacheable = annotatedClass.getAnnotation( Cacheable.class );
		final Cache effectiveCache;
		if ( cache != null ) {
			// preserve legacy behavior of circumventing SharedCacheMode when Hibernate's @Cache is used.
			isCached = true;
			effectiveCache = cache;
		}
		else {
			effectiveCache = buildCacheMock( annotatedClass.getName(), context );
			isCached = isCacheable( sharedCacheMode, cacheable );
		}
		cacheConcurrentStrategy = resolveCacheConcurrencyStrategy( effectiveCache.usage() );
		cacheRegion = effectiveCache.region();
		cacheLazyProperty = isCacheLazy( effectiveCache, annotatedClass );
	}

	private static boolean isCacheLazy(Cache effectiveCache, XClass annotatedClass) {
		if ( !effectiveCache.includeLazy() ) {
			return false;
		}
		switch ( effectiveCache.include().toLowerCase( Locale.ROOT ) ) {
			case "all":
				return true;
			case "non-lazy":
				return false;
			default:
				throw new AnnotationException( "Class '" + annotatedClass.getName()
						+ "' has a '@Cache' with undefined option 'include=\"" + effectiveCache.include() + "\"'" );
		}
	}

	private static boolean isCacheable(SharedCacheMode sharedCacheMode, Cacheable explicitCacheableAnn) {
		switch ( sharedCacheMode ) {
			case ALL:
				// all entities should be cached
				return true;
			case ENABLE_SELECTIVE:
				// only entities with @Cacheable(true) should be cached
				return explicitCacheableAnn != null && explicitCacheableAnn.value();
			case DISABLE_SELECTIVE:
				// only entities with @Cacheable(false) should not be cached
				return explicitCacheableAnn == null || explicitCacheableAnn.value();
			default:
				// treat both NONE and UNSPECIFIED the same
				return false;
		}
	}

	private static String resolveCacheConcurrencyStrategy(CacheConcurrencyStrategy strategy) {
		final org.hibernate.cache.spi.access.AccessType accessType = strategy.toAccessType();
		return accessType == null ? null : accessType.getExternalName();
	}

	private static Cache buildCacheMock(String region, MetadataBuildingContext context) {
		return new LocalCacheAnnotationStub( region, determineCacheConcurrencyStrategy( context ) );
	}

	@SuppressWarnings("ClassExplicitlyAnnotation")
	private static class LocalCacheAnnotationStub implements Cache {
		private final String region;
		private final CacheConcurrencyStrategy usage;

		private LocalCacheAnnotationStub(String region, CacheConcurrencyStrategy usage) {
			this.region = region;
			this.usage = usage;
		}

		@Override
		public CacheConcurrencyStrategy usage() {
			return usage;
		}

		@Override
		public String region() {
			return region;
		}

		@Override
		public boolean includeLazy() {
			return true;
		}

		@Override
		public String include() {
			return "all";
		}

		@Override
		public Class<? extends Annotation> annotationType() {
			return Cache.class;
		}
	}

	private static CacheConcurrencyStrategy determineCacheConcurrencyStrategy(MetadataBuildingContext context) {
		return CacheConcurrencyStrategy.fromAccessType( context.getBuildingOptions().getImplicitCacheAccessType() );
	}

	private static class EntityTableNamingStrategyHelper implements NamingStrategyHelper {
		private final String className;
		private final String entityName;
		private final String jpaEntityName;

		private EntityTableNamingStrategyHelper(String className, String entityName, String jpaEntityName) {
			this.className = className;
			this.entityName = entityName;
			this.jpaEntityName = jpaEntityName;
		}

		@Override
		public Identifier determineImplicitName(final MetadataBuildingContext buildingContext) {
			return buildingContext.getBuildingOptions().getImplicitNamingStrategy().determinePrimaryTableName(
					new ImplicitEntityNameSource() {
						private final EntityNaming entityNaming = new EntityNaming() {
							@Override
							public String getClassName() {
								return className;
							}

							@Override
							public String getEntityName() {
								return entityName;
							}

							@Override
							public String getJpaEntityName() {
								return jpaEntityName;
							}
						};

						@Override
						public EntityNaming getEntityNaming() {
							return entityNaming;
						}

						@Override
						public MetadataBuildingContext getBuildingContext() {
							return buildingContext;
						}
					}
			);
		}

		@Override
		public Identifier handleExplicitName(String explicitName, MetadataBuildingContext buildingContext) {
			return buildingContext.getMetadataCollector()
					.getDatabase()
					.getJdbcEnvironment()
					.getIdentifierHelper()
					.toIdentifier( explicitName );
		}

		@Override
		public Identifier toPhysicalName(Identifier logicalName, MetadataBuildingContext buildingContext) {
			return buildingContext.getBuildingOptions().getPhysicalNamingStrategy().toPhysicalTableName(
					logicalName,
					buildingContext.getMetadataCollector().getDatabase().getJdbcEnvironment()
			);
		}
	}

	public void bindTableForDiscriminatedSubclass(InFlightMetadataCollector.EntityTableXref superTableXref) {
		if ( !(persistentClass instanceof SingleTableSubclass) ) {
			throw new AssertionFailure(
					"Was expecting a discriminated subclass [" + SingleTableSubclass.class.getName() +
							"] but found [" + persistentClass.getClass().getName() + "] for entity [" +
							persistentClass.getEntityName() + "]"
			);
		}

		context.getMetadataCollector().addEntityTableXref(
				persistentClass.getEntityName(),
				context.getMetadataCollector().getDatabase().toIdentifier(
						context.getMetadataCollector().getLogicalTableName( superTableXref.getPrimaryTable() )
				),
				superTableXref.getPrimaryTable(),
				superTableXref
		);
	}

	public void bindTable(
			String schema,
			String catalog,
			String tableName,
			List<UniqueConstraintHolder> uniqueConstraints,
			String rowId,
			InFlightMetadataCollector.EntityTableXref denormalizedSuperTableXref) {

		final EntityTableNamingStrategyHelper namingStrategyHelper = new EntityTableNamingStrategyHelper(
				persistentClass.getClassName(),
				persistentClass.getEntityName(),
				name
		);
		final Identifier logicalName = isNotEmpty( tableName )
				? namingStrategyHelper.handleExplicitName( tableName, context )
				: namingStrategyHelper.determineImplicitName( context );

		final Table table = TableBinder.buildAndFillTable(
				schema,
				catalog,
				logicalName,
				persistentClass.isAbstract(),
				uniqueConstraints,
				context,
				subselect,
				denormalizedSuperTableXref
		);

		table.setRowId( rowId );

//		final Comment comment = annotatedClass.getAnnotation( Comment.class );
//		if ( comment != null ) {
//			table.setComment( comment.value() );
//		}

		context.getMetadataCollector().addEntityTableXref(
				persistentClass.getEntityName(),
				logicalName,
				table,
				denormalizedSuperTableXref
		);

		if ( persistentClass instanceof TableOwner ) {
			LOG.debugf( "Bind entity %s on table %s", persistentClass.getEntityName(), table.getName() );
			( (TableOwner) persistentClass ).setTable( table );
		}
		else {
			throw new AssertionFailure( "binding a table for a subclass" );
		}
	}

	public void finalSecondaryTableBinding(PropertyHolder propertyHolder) {
		 // This operation has to be done after the id definition of the persistence class.
		 // ie after the properties parsing
		final Iterator<Object> joinColumns = secondaryTableJoins.values().iterator();
		for ( Map.Entry<String, Join> entrySet : secondaryTables.entrySet() ) {
			if ( !secondaryTablesFromAnnotation.containsKey( entrySet.getKey() ) ) {
				createPrimaryColumnsToSecondaryTable( joinColumns.next(), propertyHolder, entrySet.getValue() );
			}
		}
	}

	public void finalSecondaryTableFromAnnotationBinding(PropertyHolder propertyHolder) {
		 // This operation has to be done before the end of the FK second pass processing in order
		 // to find the join columns belonging to secondary tables
		Iterator<Object> joinColumns = secondaryTableFromAnnotationJoins.values().iterator();
		for ( Map.Entry<String, Join> entrySet : secondaryTables.entrySet() ) {
			if ( secondaryTablesFromAnnotation.containsKey( entrySet.getKey() ) ) {
				createPrimaryColumnsToSecondaryTable( joinColumns.next(), propertyHolder, entrySet.getValue() );
			}
		}
	}

	private void createPrimaryColumnsToSecondaryTable(Object column, PropertyHolder propertyHolder, Join join) {
		final PrimaryKeyJoinColumn[] pkColumnsAnn = column instanceof PrimaryKeyJoinColumn[]
				? (PrimaryKeyJoinColumn[]) column
				: null;
		final JoinColumn[] joinColumnsAnn = column instanceof JoinColumn[]
				? (JoinColumn[]) column
				: null;
		final AnnotatedJoinColumns annotatedJoinColumns = pkColumnsAnn == null && joinColumnsAnn == null
				? createDefaultJoinColumn( propertyHolder )
				: createJoinColumns( propertyHolder, pkColumnsAnn, joinColumnsAnn );

		for ( AnnotatedJoinColumn joinColumn : annotatedJoinColumns.getJoinColumns() ) {
			joinColumn.forceNotNull();
		}
		bindJoinToPersistentClass( join, annotatedJoinColumns, context );
	}

	private AnnotatedJoinColumns createDefaultJoinColumn(PropertyHolder propertyHolder) {
		final AnnotatedJoinColumns joinColumns = new AnnotatedJoinColumns();
		joinColumns.setBuildingContext( context );
		joinColumns.setJoins( secondaryTables );
		joinColumns.setPropertyHolder( propertyHolder );
		buildInheritanceJoinColumn(
				null,
				null,
				persistentClass.getIdentifier(),
				joinColumns,
				context
		);
		return joinColumns;
	}

	private AnnotatedJoinColumns createJoinColumns(
			PropertyHolder propertyHolder,
			PrimaryKeyJoinColumn[] primaryKeyJoinColumns,
			JoinColumn[] joinColumns) {
		final int joinColumnCount = primaryKeyJoinColumns != null ? primaryKeyJoinColumns.length : joinColumns.length;
		if ( joinColumnCount == 0 ) {
			return createDefaultJoinColumn( propertyHolder );
		}
		else {
			final AnnotatedJoinColumns columns = new AnnotatedJoinColumns();
			columns.setBuildingContext( context );
			columns.setJoins( secondaryTables );
			columns.setPropertyHolder( propertyHolder );
			for ( int colIndex = 0; colIndex < joinColumnCount; colIndex++ ) {
				final PrimaryKeyJoinColumn primaryKeyJoinColumn = primaryKeyJoinColumns != null ? primaryKeyJoinColumns[colIndex] : null;
				final JoinColumn joinColumn = joinColumns != null ? joinColumns[colIndex] : null;
				buildInheritanceJoinColumn(
						primaryKeyJoinColumn,
						joinColumn,
						persistentClass.getIdentifier(),
						columns,
						context
				);
			}
			return columns;
		}
	}

	private void bindJoinToPersistentClass(Join join, AnnotatedJoinColumns joinColumns, MetadataBuildingContext context) {
		DependantValue key = new DependantValue( context, join.getTable(), persistentClass.getIdentifier() );
		join.setKey( key );
		setForeignKeyNameIfDefined( join );
		key.setOnDeleteAction( null );
		TableBinder.bindForeignKey( persistentClass, null, joinColumns, key, false, context );
		key.sortProperties();
		join.createPrimaryKey();
		join.createForeignKey();
		persistentClass.addJoin( join );
	}

	private void setForeignKeyNameIfDefined(Join join) {
		// just awful..
		final String tableName = join.getTable().getQuotedName();
		final org.hibernate.annotations.Table matchingTable = findMatchingComplementaryTableAnnotation( tableName );
		final SimpleValue key = (SimpleValue) join.getKey();
		if ( matchingTable != null && !matchingTable.foreignKey().name().isEmpty() ) {
			key.setForeignKeyName( matchingTable.foreignKey().name() );
		}
		else {
			final SecondaryTable jpaSecondaryTable = findMatchingSecondaryTable( join );
			if ( jpaSecondaryTable != null ) {
				final boolean noConstraintByDefault = context.getBuildingOptions().isNoConstraintByDefault();
				if ( jpaSecondaryTable.foreignKey().value() == ConstraintMode.NO_CONSTRAINT
						|| jpaSecondaryTable.foreignKey().value() == ConstraintMode.PROVIDER_DEFAULT && noConstraintByDefault ) {
					key.disableForeignKey();
				}
				else {
					key.setForeignKeyName( nullIfEmpty( jpaSecondaryTable.foreignKey().name() ) );
					key.setForeignKeyDefinition( nullIfEmpty( jpaSecondaryTable.foreignKey().foreignKeyDefinition() ) );
				}
			}
		}
	}

	private SecondaryTable findMatchingSecondaryTable(Join join) {
		final String nameToMatch = join.getTable().getQuotedName();
		final SecondaryTable secondaryTable = annotatedClass.getAnnotation( SecondaryTable.class );
		if ( secondaryTable != null && nameToMatch.equals( secondaryTable.name() ) ) {
			return secondaryTable;
		}
		final SecondaryTables secondaryTables = annotatedClass.getAnnotation( SecondaryTables.class );
		if ( secondaryTables != null ) {
			for ( SecondaryTable secondaryTablesEntry : secondaryTables.value() ) {
				if ( secondaryTablesEntry != null && nameToMatch.equals( secondaryTablesEntry.name() ) ) {
					return secondaryTablesEntry;
				}
			}
		}
		return null;
	}

	private org.hibernate.annotations.Table findMatchingComplementaryTableAnnotation(String tableName) {
		final org.hibernate.annotations.Table table = annotatedClass.getAnnotation( org.hibernate.annotations.Table.class );
		if ( table != null && tableName.equals( table.appliesTo() ) ) {
			return table;
		}
		else {
			final Tables tables = annotatedClass.getAnnotation( Tables.class );
			if ( tables != null ) {
				for (org.hibernate.annotations.Table current : tables.value()) {
					if ( tableName.equals( current.appliesTo() ) ) {
						return current;
					}
				}
			}
			return null;
		}
	}

	private SecondaryRow findMatchingSecondaryRowAnnotation(String tableName) {
		final SecondaryRow row = annotatedClass.getAnnotation( SecondaryRow.class );
		if ( row != null && ( row.table().isEmpty() || tableName.equals( row.table() ) ) ) {
			return row;
		}
		else {
			final SecondaryRows tables = annotatedClass.getAnnotation( SecondaryRows.class );
			if ( tables != null ) {
				for ( SecondaryRow current : tables.value() ) {
					if ( tableName.equals( current.table() ) ) {
						return current;
					}
				}
			}
			return null;
		}
	}

	private <T extends Annotation,R extends Annotation> T findMatchingSqlAnnotation(
			String tableName,
			Class<T> annotationType,
			Class<R> repeatableType) {
		final T sqlAnnotation = annotatedClass.getAnnotation( annotationType );
		if ( sqlAnnotation != null ) {
			if ( tableName.equals( tableMember( annotationType, sqlAnnotation ) ) ) {
				return sqlAnnotation;
			}
		}
		final R repeatable = annotatedClass.getAnnotation(repeatableType);
		if ( repeatable != null ) {
			for ( Annotation current : valueMember( repeatableType, repeatable ) ) {
				@SuppressWarnings("unchecked")
				final T sqlAnn = (T) current;
				if ( tableName.equals( tableMember( annotationType, sqlAnn ) ) ) {
					return sqlAnn;
				}
			}
		}
		return null;
	}

	private static <T extends Annotation> String tableMember(Class<T> annotationType, T sqlAnnotation) {
		if (SQLInsert.class.equals(annotationType)) {
			return ((SQLInsert) sqlAnnotation).table();
		}
		else if (SQLUpdate.class.equals(annotationType)) {
			return ((SQLUpdate) sqlAnnotation).table();
		}
		else if (SQLDelete.class.equals(annotationType)) {
			return ((SQLDelete) sqlAnnotation).table();
		}
		else if (SQLDeleteAll.class.equals(annotationType)) {
			return ((SQLDeleteAll) sqlAnnotation).table();
		}
		else {
			throw new AssertionFailure("Unknown annotation type");
		}
	}

	private static <T extends Annotation> Annotation[] valueMember(Class<T> repeatableType, T sqlAnnotation) {
		if (SQLInserts.class.equals(repeatableType)) {
			return ((SQLInserts) sqlAnnotation).value();
		}
		else if (SQLUpdates.class.equals(repeatableType)) {
			return ((SQLUpdates) sqlAnnotation).value();
		}
		else if (SQLDeletes.class.equals(repeatableType)) {
			return ((SQLDeletes) sqlAnnotation).value();
		}
		else {
			throw new AssertionFailure("Unknown annotation type");
		}
	}

	//Used for @*ToMany @JoinTable
	public Join addJoin(JoinTable joinTable, PropertyHolder holder, boolean noDelayInPkColumnCreation) {
		return addJoin(
				holder,
				noDelayInPkColumnCreation,
				false,
				joinTable.name(),
				joinTable.schema(),
				joinTable.catalog(),
				joinTable.joinColumns(),
				joinTable.uniqueConstraints()
		);
	}

	public Join addJoin(SecondaryTable secondaryTable, PropertyHolder holder, boolean noDelayInPkColumnCreation) {
		Join join = addJoin(
				holder,
				noDelayInPkColumnCreation,
				true,
				secondaryTable.name(),
				secondaryTable.schema(),
				secondaryTable.catalog(),
				secondaryTable.pkJoinColumns(),
				secondaryTable.uniqueConstraints()
		);
		TableBinder.addIndexes( join.getTable(), secondaryTable.indexes(), context );
		return join;
	}

	private Join addJoin(
			PropertyHolder propertyHolder,
			boolean noDelayInPkColumnCreation,
			boolean secondaryTable,
			String name,
			String schema,
			String catalog,
			Object joinColumns,
			UniqueConstraint[] uniqueConstraints) {
		final QualifiedTableName logicalName = new QualifiedTableName(
				Identifier.toIdentifier( catalog ),
				Identifier.toIdentifier( schema ),
				context.getMetadataCollector()
						.getDatabase()
						.getJdbcEnvironment()
						.getIdentifierHelper()
						.toIdentifier(name)
		);
		return createJoin(
				propertyHolder,
				noDelayInPkColumnCreation,
				secondaryTable,
				joinColumns,
				logicalName,
				TableBinder.buildAndFillTable(
						schema,
						catalog,
						logicalName.getTableName(),
						false,
						TableBinder.buildUniqueConstraintHolders( uniqueConstraints ),
						context
				)
		);
	}

	private Join createJoin(
			PropertyHolder propertyHolder,
			boolean noDelayInPkColumnCreation,
			boolean secondaryTable,
			Object joinColumns,
			QualifiedTableName logicalName,
			Table table) {
		final Join join = new Join();
		persistentClass.addJoin( join );

		final InFlightMetadataCollector.EntityTableXref tableXref
				= context.getMetadataCollector().getEntityTableXref( persistentClass.getEntityName() );
		assert tableXref != null : "Could not locate EntityTableXref for entity [" + persistentClass.getEntityName() + "]";
		tableXref.addSecondaryTable( logicalName, join );

		// No check constraints available on joins
		join.setTable( table );

		// Somehow keep joins() for later.
		// Has to do the work later because it needs PersistentClass id!
		LOG.debugf( "Adding secondary table to entity %s -> %s",
				persistentClass.getEntityName(), join.getTable().getName() );

		handleSecondaryRowManagement( join );
		processSecondaryTableCustomSql( join );

		if ( noDelayInPkColumnCreation ) {
			// A non-null propertyHolder means than we process the Pk creation without delay
			createPrimaryColumnsToSecondaryTable( joinColumns, propertyHolder, join );
		}
		else {
			final String quotedName = table.getQuotedName();
			if ( secondaryTable ) {
				secondaryTablesFromAnnotation.put( quotedName, join );
				secondaryTableFromAnnotationJoins.put( quotedName, joinColumns );
			}
			else {
				secondaryTableJoins.put( quotedName, joinColumns );
			}
			secondaryTables.put( quotedName, join );
		}

		return join;
	}

	private void handleSecondaryRowManagement(Join join) {
		final String tableName = join.getTable().getQuotedName();
		final org.hibernate.annotations.Table matchingTable = findMatchingComplementaryTableAnnotation( tableName );
		final SecondaryRow matchingRow = findMatchingSecondaryRowAnnotation( tableName );
		if ( matchingRow != null ) {
			join.setInverse( !matchingRow.owned() );
			join.setOptional( matchingRow.optional() );
		}
		else if ( matchingTable != null ) {
			join.setInverse( matchingTable.inverse() );
			join.setOptional( matchingTable.optional() );
		}
		else {
			//default
			join.setInverse( false );
			join.setOptional( true ); //perhaps not quite per-spec, but a Good Thing anyway
		}
	}

	private void processSecondaryTableCustomSql(Join join) {
		final String tableName = join.getTable().getQuotedName();
		final org.hibernate.annotations.Table matchingTable = findMatchingComplementaryTableAnnotation( tableName );
		final SQLInsert sqlInsert = findMatchingSqlAnnotation( tableName, SQLInsert.class, SQLInserts.class );
		if ( sqlInsert != null ) {
			join.setCustomSQLInsert(
					sqlInsert.sql().trim(),
					sqlInsert.callable(),
					fromResultCheckStyle( sqlInsert.check() )
			);
		}
		else if ( matchingTable != null ) {
			final String insertSql = matchingTable.sqlInsert().sql();
			if ( !insertSql.isEmpty() ) {
				join.setCustomSQLInsert(
						insertSql.trim(),
						matchingTable.sqlInsert().callable(),
						fromResultCheckStyle( matchingTable.sqlInsert().check() )
				);
			}
		}

		final SQLUpdate sqlUpdate = findMatchingSqlAnnotation( tableName, SQLUpdate.class, SQLUpdates.class );
		if ( sqlUpdate != null ) {
			join.setCustomSQLUpdate(
					sqlUpdate.sql().trim(),
					sqlUpdate.callable(),
					fromResultCheckStyle( sqlUpdate.check() )
			);
		}
		else if ( matchingTable != null ) {
			final String updateSql = matchingTable.sqlUpdate().sql();
			if ( !updateSql.isEmpty() ) {
				join.setCustomSQLUpdate(
						updateSql.trim(),
						matchingTable.sqlUpdate().callable(),
						fromResultCheckStyle( matchingTable.sqlUpdate().check() )
				);
			}
		}

		final SQLDelete sqlDelete = findMatchingSqlAnnotation( tableName, SQLDelete.class, SQLDeletes.class );
		if ( sqlDelete != null ) {
			join.setCustomSQLDelete(
					sqlDelete.sql().trim(),
					sqlDelete.callable(),
					fromResultCheckStyle( sqlDelete.check() )
			);
		}
		else if ( matchingTable != null ) {
			final String deleteSql = matchingTable.sqlDelete().sql();
			if ( !deleteSql.isEmpty() ) {
				join.setCustomSQLDelete(
						deleteSql.trim(),
						matchingTable.sqlDelete().callable(),
						fromResultCheckStyle( matchingTable.sqlDelete().check() )
				);
			}
		}
	}

	public java.util.Map<String, Join> getSecondaryTables() {
		return secondaryTables;
	}

	public static String getCacheConcurrencyStrategy(CacheConcurrencyStrategy strategy) {
		final org.hibernate.cache.spi.access.AccessType accessType = strategy.toAccessType();
		return accessType == null ? null : accessType.getExternalName();
	}

	public void addFilter(Filter filter) {
		filters.add( filter );
	}

	public boolean isIgnoreIdAnnotations() {
		return ignoreIdAnnotations;
	}

	public void setIgnoreIdAnnotations(boolean ignoreIdAnnotations) {
		this.ignoreIdAnnotations = ignoreIdAnnotations;
	}

	public void processComplementaryTableDefinitions(jakarta.persistence.Table table) {
		if ( table != null ) {
			TableBinder.addIndexes( persistentClass.getTable(), table.indexes(), context );
		}
	}

	public void processComplementaryTableDefinitions(org.hibernate.annotations.Table table) {
		if ( table != null ) {
			final Table appliedTable = findTable( table.appliesTo() );
			if ( !table.comment().isEmpty() ) {
				appliedTable.setComment( table.comment() );
			}
			if ( !table.checkConstraint().isEmpty() ) {
				appliedTable.addCheckConstraint( table.checkConstraint() );
			}
			TableBinder.addIndexes( appliedTable, table.indexes(), context );
		}
	}

	private Table findTable(String tableName) {
		for ( Table table : persistentClass.getTableClosure() ) {
			if ( table.getQuotedName().equals( tableName ) ) {
				//we are in the correct table to find columns
				return table;
			}
		}
		//maybe a join/secondary table
		for ( Join join : secondaryTables.values() ) {
			if ( join.getTable().getQuotedName().equals( tableName ) ) {
				return join.getTable();
			}
		}
		throw new AnnotationException( "Entity '" + name
				+ "' has a '@org.hibernate.annotations.Table' annotation which 'appliesTo' an unknown table named '"
				+ tableName + "'" );
	}

	public void processComplementaryTableDefinitions(Tables tables) {
		if ( tables != null ) {
			for ( org.hibernate.annotations.Table table : tables.value() ) {
				processComplementaryTableDefinitions( table );
			}
		}
	}

	public AccessType getPropertyAccessType() {
		return propertyAccessType;
	}

	public void setPropertyAccessType(AccessType propertyAccessType) {
		this.propertyAccessType = getExplicitAccessType( annotatedClass );
		// only set the access type if there is no explicit access type for this class
		if ( this.propertyAccessType == null ) {
			this.propertyAccessType = propertyAccessType;
		}
	}

	public AccessType getPropertyAccessor(XAnnotatedElement element) {
		final AccessType accessType = getExplicitAccessType( element );
		return accessType == null ? propertyAccessType : accessType;
	}

	public AccessType getExplicitAccessType(XAnnotatedElement element) {
		AccessType accessType = null;
		if ( element != null ) {
			final Access access = element.getAnnotation( Access.class );
			if ( access != null ) {
				accessType = AccessType.getAccessStrategy( access.value() );
			}
		}
		return accessType;
	}

	/**
	 * Process the filters defined on the given class, as well as all filters
	 * defined on the MappedSuperclass(es) in the inheritance hierarchy
	 */
	public void bindFiltersInHierarchy() {

		bindFilters( annotatedClass );

		XClass classToProcess = annotatedClass.getSuperclass();
		while ( classToProcess != null ) {
			final AnnotatedClassType classType = context.getMetadataCollector().getClassType( classToProcess );
			if ( classType == MAPPED_SUPERCLASS ) {
				bindFilters( classToProcess );
			}
			else {
				break;
			}
			classToProcess = classToProcess.getSuperclass();
		}
	}

	private void bindFilters(XAnnotatedElement element) {
		final Filters filters = getOverridableAnnotation( element, Filters.class, context );
		if ( filters != null ) {
			for ( Filter filter : filters.value() ) {
				addFilter( filter );
			}
		}
		final Filter filter = element.getAnnotation( Filter.class );
		if ( filter != null ) {
			addFilter( filter );
		}
	}
}
