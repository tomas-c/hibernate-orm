/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.persister.collection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.hibernate.AssertionFailure;
import org.hibernate.FetchMode;
import org.hibernate.Filter;
import org.hibernate.HibernateException;
import org.hibernate.Internal;
import org.hibernate.LockOptions;
import org.hibernate.MappingException;
import org.hibernate.QueryException;
import org.hibernate.Remove;
import org.hibernate.TransientObjectException;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.access.CollectionDataAccess;
import org.hibernate.cache.spi.entry.CacheEntryStructure;
import org.hibernate.cache.spi.entry.StructuredCollectionCacheEntry;
import org.hibernate.cache.spi.entry.StructuredMapCacheEntry;
import org.hibernate.cache.spi.entry.UnstructuredCacheEntry;
import org.hibernate.collection.spi.CollectionSemantics;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.mutation.ParameterUsage;
import org.hibernate.engine.jdbc.mutation.internal.MutationQueryOptions;
import org.hibernate.engine.jdbc.spi.JdbcCoordinator;
import org.hibernate.engine.jdbc.spi.SqlExceptionHelper;
import org.hibernate.engine.profile.Fetch;
import org.hibernate.engine.profile.FetchProfile;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.ExecuteUpdateResultCheckStyle;
import org.hibernate.engine.spi.LoadQueryInfluencers;
import org.hibernate.engine.spi.PersistenceContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.engine.spi.SubselectFetch;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.Generator;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.internal.FilterAliasGenerator;
import org.hibernate.internal.FilterHelper;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.jdbc.Expectation;
import org.hibernate.jdbc.Expectations;
import org.hibernate.loader.ast.internal.CollectionElementLoaderByIndex;
import org.hibernate.loader.ast.internal.CollectionLoaderBatchKey;
import org.hibernate.loader.ast.internal.CollectionLoaderNamedQuery;
import org.hibernate.loader.ast.internal.CollectionLoaderSingleKey;
import org.hibernate.loader.ast.internal.CollectionLoaderSubSelectFetch;
import org.hibernate.loader.ast.internal.LoaderSqlAstCreationState;
import org.hibernate.loader.ast.spi.CollectionLoader;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Formula;
import org.hibernate.mapping.IdentifierCollection;
import org.hibernate.mapping.IndexedCollection;
import org.hibernate.mapping.Selectable;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.Value;
import org.hibernate.metadata.CollectionMetadata;
import org.hibernate.metamodel.CollectionClassification;
import org.hibernate.metamodel.mapping.EmbeddableValuedModelPart;
import org.hibernate.metamodel.mapping.ForeignKeyDescriptor;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.metamodel.mapping.internal.MappingModelCreationHelper;
import org.hibernate.metamodel.mapping.internal.PluralAttributeMappingImpl;
import org.hibernate.metamodel.model.domain.NavigableRole;
import org.hibernate.metamodel.spi.RuntimeModelCreationContext;
import org.hibernate.persister.collection.mutation.CollectionMutationTarget;
import org.hibernate.persister.collection.mutation.CollectionTableMapping;
import org.hibernate.persister.collection.mutation.RemoveCoordinator;
import org.hibernate.persister.collection.mutation.RowMutationOperations;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.persister.entity.Joinable;
import org.hibernate.persister.entity.PropertyMapping;
import org.hibernate.persister.internal.SqlFragmentPredicate;
import org.hibernate.persister.spi.PersisterCreationContext;
import org.hibernate.pretty.MessageHelper;
import org.hibernate.query.named.NamedQueryMemento;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.spi.NavigablePath;
import org.hibernate.sql.Alias;
import org.hibernate.sql.SimpleSelect;
import org.hibernate.sql.Template;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.spi.SimpleFromClauseAccessImpl;
import org.hibernate.sql.ast.spi.SqlAliasBaseConstant;
import org.hibernate.sql.ast.spi.SqlAliasBaseManager;
import org.hibernate.sql.ast.spi.SqlAstCreationState;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.expression.AliasedExpression;
import org.hibernate.sql.ast.tree.expression.ColumnReference;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.ast.tree.predicate.Predicate;
import org.hibernate.sql.ast.tree.select.QuerySpec;
import org.hibernate.sql.ast.tree.select.SelectClause;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.model.ModelMutationLogging;
import org.hibernate.sql.model.MutationType;
import org.hibernate.sql.model.TableMapping;
import org.hibernate.sql.model.TableMapping.MutationDetails;
import org.hibernate.sql.model.ast.ColumnValueBinding;
import org.hibernate.sql.model.ast.ColumnValueParameter;
import org.hibernate.sql.model.ast.ColumnValueParameterList;
import org.hibernate.sql.model.ast.ColumnWriteFragment;
import org.hibernate.sql.model.ast.MutatingTableReference;
import org.hibernate.sql.model.ast.RestrictedTableMutation;
import org.hibernate.sql.model.internal.TableDeleteStandard;
import org.hibernate.sql.model.jdbc.JdbcDeleteMutation;
import org.hibernate.sql.model.jdbc.JdbcMutationOperation;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.internal.ImmutableFetchList;
import org.hibernate.sql.results.internal.SqlSelectionImpl;
import org.hibernate.type.CollectionType;
import org.hibernate.type.CompositeType;
import org.hibernate.type.EntityType;
import org.hibernate.type.Type;

import static org.hibernate.internal.util.collections.CollectionHelper.arrayList;
import static org.hibernate.sql.model.ModelMutationLogging.MODEL_MUTATION_LOGGER;

/**
 * Base implementation of the {@code QueryableCollection} interface.
 *
 * @author Gavin King
 *
 * @see BasicCollectionPersister
 * @see OneToManyPersister
 */
@Internal
public abstract class AbstractCollectionPersister
		implements CollectionPersister, CollectionMutationTarget, PluralAttributeMappingImpl.Aware, DeprecatedCollectionStuff {

	private final NavigableRole navigableRole;
	private final CollectionSemantics<?,?> collectionSemantics;
	private final EntityPersister ownerPersister;
	private final SessionFactoryImplementor factory;

	protected final String qualifiedTableName;
	private final CollectionTableMapping tableMapping;

	private final String sqlSelectSizeString;
	private final String sqlDetectRowByIndexString;
	private final String sqlDetectRowByElementString;

	protected final boolean hasWhere;
	protected final String sqlWhereString;
	private final String sqlWhereStringTemplate;

	private final boolean hasOrder;
	private final boolean hasManyToManyOrder;

	private final String mappedByProperty;

	protected final boolean indexContainsFormula;
	protected final boolean elementIsPureFormula;

	// columns
	protected final String[] keyColumnNames;
	protected final String[] indexColumnNames;
	protected final String[] indexFormulaTemplates;
	protected final String[] indexFormulas;
	protected final boolean[] indexColumnIsGettable;
	protected final boolean[] indexColumnIsSettable;
	protected final String[] elementColumnNames;
	protected final String[] elementColumnWriters;
	protected final String[] elementColumnReaders;
	protected final String[] elementColumnReaderTemplates;
	protected final String[] elementFormulaTemplates;
	protected final String[] elementFormulas;
	protected final boolean[] elementColumnIsGettable;
	protected final boolean[] elementColumnIsSettable;

	protected final String identifierColumnName;

	private final String queryLoaderName;

	private final boolean isPrimitiveArray;
	private final boolean isLazy;
	private final boolean isExtraLazy;
	protected final boolean isInverse;
	private final boolean isMutable;
	private final boolean isVersioned;
	protected final int batchSize;
	private final FetchMode fetchMode;
	private final boolean hasOrphanDelete;
	private final boolean subselectLoadable;

	// extra information about the element type
	private final Class<?> elementClass;

	private final Dialect dialect;
	protected final SqlExceptionHelper sqlExceptionHelper;
	private final BeforeExecutionGenerator identifierGenerator;
	private final PropertyMapping elementPropertyMapping;
	private final EntityPersister elementPersister;
	private final CollectionDataAccess cacheAccessStrategy;

	private final CacheEntryStructure cacheEntryStructure;

	// dynamic filters for the collection
	private final FilterHelper filterHelper;

	// dynamic filters specifically for many-to-many inside the collection
	private final FilterHelper manyToManyFilterHelper;

	private final String manyToManyWhereString;
	private final String manyToManyWhereTemplate;

	private final String[] spaces;

	private final Comparator<?> comparator;

	private CollectionLoader collectionLoader;
	private volatile CollectionLoader standardCollectionLoader;
	private CollectionElementLoaderByIndex collectionElementLoaderByIndex;

	private PluralAttributeMapping attributeMapping;


	@Deprecated(since = "6.0")
	public AbstractCollectionPersister(
			Collection collectionBootDescriptor,
			CollectionDataAccess cacheAccessStrategy,
			PersisterCreationContext creationContext) throws MappingException, CacheException {
		this( collectionBootDescriptor, cacheAccessStrategy, (RuntimeModelCreationContext) creationContext );
	}

	public AbstractCollectionPersister(
			Collection collectionBootDescriptor,
			CollectionDataAccess cacheAccessStrategy,
			RuntimeModelCreationContext creationContext) throws MappingException, CacheException {
		this.factory = creationContext.getSessionFactory();
		this.collectionSemantics = creationContext.getBootstrapContext()
				.getMetadataBuildingOptions()
				.getPersistentCollectionRepresentationResolver()
				.resolveRepresentation( collectionBootDescriptor );

		this.cacheAccessStrategy = cacheAccessStrategy;
		if ( creationContext.getSessionFactoryOptions().isStructuredCacheEntriesEnabled() ) {
			cacheEntryStructure = collectionBootDescriptor.isMap()
					? StructuredMapCacheEntry.INSTANCE
					: StructuredCollectionCacheEntry.INSTANCE;
		}
		else {
			cacheEntryStructure = UnstructuredCacheEntry.INSTANCE;
		}

		dialect = creationContext.getDialect();
		sqlExceptionHelper = creationContext.getJdbcServices().getSqlExceptionHelper();
		collectionType = collectionBootDescriptor.getCollectionType();
		navigableRole = new NavigableRole( collectionBootDescriptor.getRole() );
		ownerPersister = creationContext.getDomainModel().getEntityDescriptor( collectionBootDescriptor.getOwnerEntityName() );
		queryLoaderName = collectionBootDescriptor.getLoaderName();
		isMutable = collectionBootDescriptor.isMutable();
		mappedByProperty = collectionBootDescriptor.getMappedByProperty();

		final Value elementBootDescriptor = collectionBootDescriptor.getElement();
		final Table table = collectionBootDescriptor.getCollectionTable();

		fetchMode = elementBootDescriptor.getFetchMode();
		elementType = elementBootDescriptor.getType();
		// isSet = collectionBinding.isSet();
		// isSorted = collectionBinding.isSorted();
		isPrimitiveArray = collectionBootDescriptor.isPrimitiveArray();
		subselectLoadable = collectionBootDescriptor.isSubselectLoadable();

		qualifiedTableName = determineTableName( table );

		int spacesSize = 1 + collectionBootDescriptor.getSynchronizedTables().size();
		spaces = new String[spacesSize];
		Iterator<String> tables = collectionBootDescriptor.getSynchronizedTables().iterator();
		for ( int i = 1; i < spacesSize; i++ ) {
			spaces[i] = tables.next();
		}

		if ( StringHelper.isNotEmpty( collectionBootDescriptor.getWhere() ) ) {
			hasWhere = true;
			sqlWhereString = "(" + collectionBootDescriptor.getWhere() + ") ";
			sqlWhereStringTemplate = Template.renderWhereStringTemplate(
					sqlWhereString,
					dialect,
					creationContext.getTypeConfiguration(),
					creationContext.getFunctionRegistry()
			);
		}
		else {
			hasWhere = false;
			sqlWhereString = null;
			sqlWhereStringTemplate = null;
		}

		hasOrphanDelete = collectionBootDescriptor.hasOrphanDelete();

		int batch = collectionBootDescriptor.getBatchSize();
		if ( batch == -1 ) {
			batch = creationContext.getSessionFactoryOptions().getDefaultBatchFetchSize();
		}
		batchSize = batch;

		isVersioned = collectionBootDescriptor.isOptimisticLocked();

		// KEY

		keyType = collectionBootDescriptor.getKey().getType();
		int keySpan = collectionBootDescriptor.getKey().getColumnSpan();
		keyColumnNames = new String[keySpan];
		keyColumnAliases = new String[keySpan];
		int k = 0;
		for ( Column column: collectionBootDescriptor.getKey().getColumns() ) {
			// NativeSQL: collect key column and auto-aliases
			keyColumnNames[k] = column.getQuotedName( dialect );
			keyColumnAliases[k] = column.getAlias( dialect, table );
			k++;
		}

		// unquotedKeyColumnNames = StringHelper.unQuote(keyColumnAliases);

		// ELEMENT

		if ( elementType.isEntityType() ) {
			String entityName = ( (EntityType) elementType ).getAssociatedEntityName();
			elementPersister = creationContext.getDomainModel().getEntityDescriptor( entityName );
			// NativeSQL: collect element column and auto-aliases

		}
		else {
			elementPersister = null;
		}
		// Defer this after the element persister was determined, because it is needed in OneToManyPersister#getTableName()
		spaces[0] = getTableName();

		int elementSpan = elementBootDescriptor.getColumnSpan();
		elementColumnAliases = new String[elementSpan];
		elementColumnNames = new String[elementSpan];
		elementColumnWriters = new String[elementSpan];
		elementColumnReaders = new String[elementSpan];
		elementColumnReaderTemplates = new String[elementSpan];
		elementFormulaTemplates = new String[elementSpan];
		elementFormulas = new String[elementSpan];
		elementColumnIsSettable = new boolean[elementSpan];
		elementColumnIsGettable = new boolean[elementSpan];
		boolean isPureFormula = true;
		boolean oneToMany = collectionBootDescriptor.isOneToMany();
		boolean[] columnInsertability = null;
		if ( !oneToMany ) {
			columnInsertability = elementBootDescriptor.getColumnInsertability();
		}
		int j = 0;
		for ( Selectable selectable: elementBootDescriptor.getSelectables() ) {
			elementColumnAliases[j] = selectable.getAlias( dialect, table );
			if ( selectable.isFormula() ) {
				Formula form = (Formula) selectable;
				elementFormulaTemplates[j] = form.getTemplate(
						dialect,
						creationContext.getTypeConfiguration(),
						creationContext.getFunctionRegistry()
				);
				elementFormulas[j] = form.getFormula();
			}
			else {
				Column col = (Column) selectable;
				elementColumnNames[j] = col.getQuotedName( dialect );
				elementColumnWriters[j] = col.getWriteExpr( elementBootDescriptor.getSelectableType( factory, j ), dialect );
				elementColumnReaders[j] = col.getReadExpr( dialect );
				elementColumnReaderTemplates[j] = col.getTemplate(
						dialect,
						creationContext.getTypeConfiguration(),
						creationContext.getFunctionRegistry()
				);
				elementColumnIsGettable[j] = true;
				if ( elementType.isComponentType() ) {
					// Implements desired behavior specifically for @ElementCollection mappings.
					elementColumnIsSettable[j] = columnInsertability[j];
				}
				else {
					// Preserves legacy non-@ElementCollection behavior
					elementColumnIsSettable[j] = true;
				}
				isPureFormula = false;
			}
			j++;
		}
		elementIsPureFormula = isPureFormula;

		// INDEX AND ROW SELECT

		final boolean hasIndex = collectionBootDescriptor.isIndexed();
		if ( hasIndex ) {
			// NativeSQL: collect index column and auto-aliases
			IndexedCollection indexedCollection = (IndexedCollection) collectionBootDescriptor;
			indexType = indexedCollection.getIndex().getType();
			int indexSpan = indexedCollection.getIndex().getColumnSpan();
			boolean[] indexColumnInsertability = indexedCollection.getIndex().getColumnInsertability();
			boolean[] indexColumnUpdatability = indexedCollection.getIndex().getColumnUpdateability();
			indexColumnNames = new String[indexSpan];
			indexFormulaTemplates = new String[indexSpan];
			indexFormulas = new String[indexSpan];
			indexColumnIsGettable = new boolean[indexSpan];
			indexColumnIsSettable = new boolean[indexSpan];
			indexColumnAliases = new String[indexSpan];
			int i = 0;
			boolean hasFormula = false;
			for ( Selectable s: indexedCollection.getIndex().getSelectables() ) {
				indexColumnAliases[i] = s.getAlias( dialect );
				if ( s.isFormula() ) {
					Formula indexForm = (Formula) s;
					indexFormulaTemplates[i] = indexForm.getTemplate(
							dialect,
							creationContext.getTypeConfiguration(),
							creationContext.getFunctionRegistry()
					);
					indexFormulas[i] = indexForm.getFormula();
					hasFormula = true;
				}
				// Treat a mapped-by index like a formula to avoid trying to set it in insert/update
				// Previously this was a sub-query formula, but was changed to represent the proper mapping
				// which enables optimizations for queries. The old insert/update code wasn't adapted yet though.
				// For now, this is good enough, because the formula is never used anymore,
				// since all read paths go through the new code that can properly handle this case
				else if ( indexedCollection instanceof org.hibernate.mapping.Map
						&& ( (org.hibernate.mapping.Map) indexedCollection ).getMapKeyPropertyName() != null ) {
					Column indexCol = (Column) s;
					indexFormulaTemplates[i] = Template.TEMPLATE + indexCol.getQuotedName( dialect );
					indexFormulas[i] = indexCol.getQuotedName( dialect );
					hasFormula = true;
				}
				else {
					Column indexCol = (Column) s;
					indexColumnNames[i] = indexCol.getQuotedName( dialect );
					indexColumnIsGettable[i] = true;
					indexColumnIsSettable[i] = indexColumnInsertability[i] || indexColumnUpdatability[i];
				}
				i++;
			}
			indexContainsFormula = hasFormula;
		}
		else {
			indexContainsFormula = false;
			indexColumnIsGettable = null;
			indexColumnIsSettable = null;
			indexFormulaTemplates = null;
			indexFormulas = null;
			indexType = null;
			indexColumnNames = null;
			indexColumnAliases = null;
		}

		final boolean hasIdentifier = collectionBootDescriptor.isIdentified();
		if ( hasIdentifier ) {
			if ( collectionBootDescriptor.isOneToMany() ) {
				throw new MappingException( "one-to-many collections with identifiers are not supported" );
			}
			//noinspection ConstantConditions
			IdentifierCollection idColl = (IdentifierCollection) collectionBootDescriptor;
			identifierType = idColl.getIdentifier().getType();
			Column col = idColl.getIdentifier().getColumns().get(0);
			identifierColumnName = col.getQuotedName( dialect );
			identifierColumnAlias = col.getAlias( dialect );
			identifierGenerator = createGenerator( creationContext, idColl );
		}
		else {
			identifierType = null;
			identifierColumnName = null;
			identifierColumnAlias = null;
			identifierGenerator = null;
		}

		// GENERATE THE SQL:

		sqlSelectSizeString = generateSelectSizeString( collectionBootDescriptor.isIndexed() && !collectionBootDescriptor.isMap() );
		sqlDetectRowByIndexString = generateDetectRowByIndexString();
		sqlDetectRowByElementString = generateDetectRowByElementString();

		isLazy = collectionBootDescriptor.isLazy();
		isExtraLazy = collectionBootDescriptor.isExtraLazy();

		isInverse = collectionBootDescriptor.isInverse();

		if ( collectionBootDescriptor.isArray() ) {
			elementClass = ( (org.hibernate.mapping.Array) collectionBootDescriptor ).getElementClass();
		}
		else {
			// for non-arrays, we don't need to know the element class
			elementClass = null; // elementType.returnedClass();
		}

		if ( elementType.isComponentType() ) {
			elementPropertyMapping = new CompositeElementPropertyMapping(
					elementColumnNames,
					elementColumnReaders,
					elementColumnReaderTemplates,
					elementFormulaTemplates,
					(CompositeType) elementType,
					creationContext.getMetadata()
			);
		}
		else if ( !elementType.isEntityType() ) {
			elementPropertyMapping = new ElementPropertyMapping( elementColumnNames, elementType );
		}
		else {
			// not all entity-persisters implement PropertyMapping!
			if ( elementPersister instanceof PropertyMapping ) {
				elementPropertyMapping = (PropertyMapping) elementPersister;
			}
			else {
				elementPropertyMapping = new ElementPropertyMapping( elementColumnNames, elementType );
			}
		}

		hasOrder = collectionBootDescriptor.getOrderBy() != null;
		hasManyToManyOrder = collectionBootDescriptor.getManyToManyOrdering() != null;

		// Handle any filters applied to this collectionBinding
		if ( collectionBootDescriptor.getFilters().isEmpty() ) {
			filterHelper = null;
		}
		else {
			filterHelper = new FilterHelper( collectionBootDescriptor.getFilters(), factory);
		}

		// Handle any filters applied to this collectionBinding for many-to-many
		if ( collectionBootDescriptor.getManyToManyFilters().isEmpty() ) {
			manyToManyFilterHelper = null;
		}
		else {
			manyToManyFilterHelper = new FilterHelper( collectionBootDescriptor.getManyToManyFilters(), factory);
		}

		if ( StringHelper.isEmpty( collectionBootDescriptor.getManyToManyWhere() ) ) {
			manyToManyWhereString = null;
			manyToManyWhereTemplate = null;
		}
		else {
			manyToManyWhereString = "( " + collectionBootDescriptor.getManyToManyWhere() + ")";
			manyToManyWhereTemplate = Template.renderWhereStringTemplate(
					manyToManyWhereString,
					creationContext.getDialect(),
					creationContext.getTypeConfiguration(),
					creationContext.getFunctionRegistry()
			);
		}

		comparator = collectionBootDescriptor.getComparator();

		initCollectionPropertyMap();

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// "mapping model"

		if ( queryLoaderName != null ) {
			final NamedQueryMemento namedQueryMemento = factory
					.getQueryEngine()
					.getNamedObjectRepository()
					.resolve( factory, collectionBootDescriptor.getMetadata(), queryLoaderName );
			if ( namedQueryMemento == null ) {
				throw new IllegalArgumentException( "Could not resolve named load-query [" + navigableRole + "] : " + queryLoaderName );
			}
		}

		tableMapping = buildCollectionTableMapping( collectionBootDescriptor, getTableName(), getCollectionSpaces() );
	}

	private BeforeExecutionGenerator createGenerator(RuntimeModelCreationContext context, IdentifierCollection collection) {
		final Generator generator = collection.getIdentifier().createGenerator(
				context.getBootstrapContext().getIdentifierGeneratorFactory(),
				context.getDialect(),
				null
		);
		if ( generator.generatedOnExecution() ) {
			throw new MappingException("must be an BeforeExecutionGenerator"); //TODO fix message
		}
		if ( generator instanceof IdentifierGenerator ) {
			( (IdentifierGenerator) generator ).initialize( context.getSqlStringGenerationContext() );
		}
		return (BeforeExecutionGenerator) generator;
	}

	@Override
	public NavigableRole getNavigableRole() {
		return navigableRole;
	}

	@Override
	public Comparator<?> getSortingComparator() {
		return comparator;
	}

	protected String determineTableName(Table table) {
		return MappingModelCreationHelper.getTableIdentifierExpression( table, factory );
	}

	@Override
	public void postInstantiate() throws MappingException {
		if ( queryLoaderName == null ) {
			collectionLoader = createCollectionLoader( LoadQueryInfluencers.NONE );
		}
		else {
			// We pass null as metamodel because we did the initialization during construction already
			final NamedQueryMemento namedQueryMemento = factory.getQueryEngine().getNamedObjectRepository()
					.resolve( factory, null, queryLoaderName );
			collectionLoader = new CollectionLoaderNamedQuery( this, namedQueryMemento );
		}

		if ( attributeMapping.getIndexDescriptor() != null ) {
			collectionElementLoaderByIndex = new CollectionElementLoaderByIndex(
					attributeMapping,
					LoadQueryInfluencers.NONE,
					getFactory()
			);
		}

		logStaticSQL();
	}

	protected void logStaticSQL() {
		if ( !ModelMutationLogging.MODEL_MUTATION_LOGGER_DEBUG_ENABLED ) {
			return;
		}

		MODEL_MUTATION_LOGGER.debugf( "Static SQL for collection: %s", getRole() );

		if ( getRowMutationOperations().hasInsertRow() ) {
			final String insertRowSql = getRowMutationOperations().getInsertRowOperation().getSqlString();
			if ( insertRowSql != null ) {
				MODEL_MUTATION_LOGGER.debugf( " Row insert: %s", insertRowSql );
			}
		}

		if ( getRowMutationOperations().hasUpdateRow() ) {
			final String updateRowSql = getRowMutationOperations().getUpdateRowOperation().getSqlString();
			if ( updateRowSql != null ) {
				MODEL_MUTATION_LOGGER.debugf( " Row update: %s", updateRowSql );
			}
		}

		if ( getRowMutationOperations().hasDeleteRow() ) {
			final String deleteRowSql = getRowMutationOperations().getDeleteRowOperation().getSqlString();
			if ( deleteRowSql != null ) {
				MODEL_MUTATION_LOGGER.debugf( " Row delete: %s", deleteRowSql );
			}
		}

		final String deleteAllSql = getRemoveCoordinator().getSqlString();
		if ( deleteAllSql != null ) {
			MODEL_MUTATION_LOGGER.debugf( " One-shot delete: %s", deleteAllSql );
		}
	}

	@Override
	public void initialize(Object key, SharedSessionContractImplementor session) throws HibernateException {
		determineLoaderToUse( key, session ).load( key, session );
	}

	// lazily initialize instance field via 'double-checked locking'
	// see https://en.wikipedia.org/wiki/Double-checked_locking on why 'volatile' and local copy is used
	protected CollectionLoader getStandardCollectionLoader() {
		CollectionLoader localCopy = standardCollectionLoader;
		if ( localCopy == null ) {
			synchronized (this) {
				localCopy = standardCollectionLoader;
				if ( localCopy == null ) {
					if ( queryLoaderName != null ) {
						localCopy = collectionLoader;
					}
					else {
						localCopy = createCollectionLoader( LoadQueryInfluencers.NONE );
					}
					standardCollectionLoader  = localCopy;
				}
			}
		}
		return localCopy;
	}

	protected CollectionLoader determineLoaderToUse(Object key, SharedSessionContractImplementor session) {
		if ( queryLoaderName != null ) {
			// if there is a user-specified loader, return that
			return getStandardCollectionLoader();
		}

		final CollectionLoader subSelectLoader = resolveSubSelectLoader( key, session );
		if ( subSelectLoader != null ) {
			return subSelectLoader;
		}

		if ( ! session.getLoadQueryInfluencers().hasEnabledFilters() && ! isAffectedByEnabledFetchProfiles( session.getLoadQueryInfluencers() ) ) {
			return getStandardCollectionLoader();
		}

		return createCollectionLoader( session.getLoadQueryInfluencers() );
	}

	private CollectionLoader resolveSubSelectLoader(Object key, SharedSessionContractImplementor session) {
		if ( !isSubselectLoadable() ) {
			return null;
		}

		final PersistenceContext persistenceContext = session.getPersistenceContextInternal();

		final EntityKey ownerEntityKey = session.generateEntityKey( key, getOwnerEntityPersister() );
		final SubselectFetch subselect = persistenceContext.getBatchFetchQueue().getSubselect( ownerEntityKey );
		if ( subselect == null ) {
			return null;
		}

		// Take care of any entities that might have
		// been evicted!
		subselect.getResultingEntityKeys().removeIf( o -> !persistenceContext.containsEntity( o ) );

		// Run a subquery loader
		return createSubSelectLoader( subselect, session );
	}

	protected CollectionLoader createSubSelectLoader(SubselectFetch subselect, SharedSessionContractImplementor session) {
		//noinspection RedundantCast
		return new CollectionLoaderSubSelectFetch(
				attributeMapping,
				(DomainResult<?>) null,
				subselect,
				session
		);
	}

	protected CollectionLoader createCollectionLoader(LoadQueryInfluencers loadQueryInfluencers) {
		final int batchSize = getBatchSize();
		if ( batchSize > 1 ) {
			return new CollectionLoaderBatchKey( attributeMapping, batchSize, loadQueryInfluencers, getFactory() );
		}


		return new CollectionLoaderSingleKey( attributeMapping, loadQueryInfluencers, getFactory() );
	}

	@Override
	public CollectionDataAccess getCacheAccessStrategy() {
		return cacheAccessStrategy;
	}

	@Override
	public boolean hasCache() {
		return cacheAccessStrategy != null;
	}

	protected abstract RowMutationOperations getRowMutationOperations();
	protected abstract RemoveCoordinator getRemoveCoordinator();

	@Override
	public String getSQLOrderByString(String alias) {
		if ( hasOrdering() ) {
			throw new UnsupportedOperationException();
		}

		return "";
	}

	@Override
	public String getManyToManyOrderByString(String alias) {
		if ( hasManyToManyOrdering() ) {
			throw new UnsupportedOperationException();
		}

		return "";
	}

	@Override
	public FetchMode getFetchMode() {
		return fetchMode;
	}

	@Override
	public boolean hasOrdering() {
		return hasOrder;
	}

	@Override
	public boolean hasManyToManyOrdering() {
		return isManyToMany() && hasManyToManyOrder;
	}

	@Override
	public boolean hasWhere() {
		return hasWhere;
	}

	/**
	 * Return the element class of an array, or null otherwise.  needed by arrays
	 */
	@Override
	public Class<?> getElementClass() {
		return elementClass;
	}

	/**
	 * @deprecated No longer used.
	 */
	@Deprecated(forRemoval = true)
	@Remove
	protected Object decrementIndexByBase(Object index) {
		final int baseIndex = attributeMapping.getIndexMetadata().getListIndexBase();
		if ( baseIndex > 0 ) {
			index = (Integer)index - baseIndex;
		}
		return index;
	}

	protected Object incrementIndexByBase(Object index) {
		final int baseIndex = attributeMapping.getIndexMetadata().getListIndexBase();
		if ( baseIndex > 0  ) {
			index = (Integer)index + baseIndex;
		}
		return index;
	}

	@Override
	public boolean isPrimitiveArray() {
		return isPrimitiveArray;
	}

	@Override
	public boolean isArray() {
		return collectionSemantics.getCollectionClassification() == CollectionClassification.ARRAY;
	}

	@Override
	public String getIdentifierColumnName() {
		if ( collectionSemantics.getCollectionClassification() == CollectionClassification.ID_BAG ) {
			return identifierColumnName;
		}
		else {
			return null;
		}
	}

	/**
	 * Generate a list of collection index, key and element columns
	 */
	@Override
	public String selectFragment(String alias, String columnSuffix) {
		final PluralAttributeMapping attributeMapping = getAttributeMapping();
		final QuerySpec rootQuerySpec = new QuerySpec( true );
		final LoaderSqlAstCreationState sqlAstCreationState = new LoaderSqlAstCreationState(
				rootQuerySpec,
				new SqlAliasBaseManager(),
				new SimpleFromClauseAccessImpl(),
				LockOptions.NONE,
				(fetchParent, creationState) -> ImmutableFetchList.EMPTY,
				true,
				LoadQueryInfluencers.NONE,
				getFactory()
		);

		final NavigablePath entityPath = new NavigablePath( attributeMapping.getRootPathName() );
		final TableGroup rootTableGroup = attributeMapping.createRootTableGroup(
				true,
				entityPath,
				null,
				new SqlAliasBaseConstant( alias ),
				() -> p -> {},
				sqlAstCreationState
		);

		rootQuerySpec.getFromClause().addRoot( rootTableGroup );
		sqlAstCreationState.getFromClauseAccess().registerTableGroup( entityPath, rootTableGroup );

		attributeMapping.createDomainResult( entityPath, rootTableGroup, null, sqlAstCreationState );

		// Wrap expressions with aliases
		final SelectClause selectClause = rootQuerySpec.getSelectClause();
		final java.util.List<SqlSelection> sqlSelections = selectClause.getSqlSelections();
		int i = 0;
		for ( String keyAlias : keyColumnAliases ) {
			sqlSelections.set(
					i,
					new SqlSelectionImpl(
							i,
							i + 1,
							new AliasedExpression( sqlSelections.get( i ).getExpression(), keyAlias + columnSuffix )
					)
			);
			i++;
		}

		if ( hasIndex() ) {
			for ( String indexAlias : indexColumnAliases ) {
				sqlSelections.set(
						i,
						new SqlSelectionImpl(
								i,
								i + 1,
								new AliasedExpression( sqlSelections.get( i ).getExpression(), indexAlias + columnSuffix )
						)
				);
				i++;
			}
		}
		if ( collectionSemantics.getCollectionClassification() == CollectionClassification.ID_BAG ) {
			sqlSelections.set(
					i,
					new SqlSelectionImpl(
							i,
							i + 1,
							new AliasedExpression( sqlSelections.get( i ).getExpression(), identifierColumnAlias + columnSuffix )
					)
			);
			i++;
		}

		for ( int columnIndex = 0; i < sqlSelections.size(); i++, columnIndex++ ) {
			final SqlSelection sqlSelection = sqlSelections.get( i );
			sqlSelections.set(
					i,
					new SqlSelectionImpl(
							sqlSelection.getValuesArrayPosition(),
							sqlSelection.getJdbcResultSetIndex(),
							new AliasedExpression( sqlSelection.getExpression(), elementColumnAliases[columnIndex] + columnSuffix )
					)
			);
		}

		final String sql = getFactory().getJdbcServices()
				.getDialect()
				.getSqlAstTranslatorFactory()
				.buildSelectTranslator( getFactory(), new SelectStatement( rootQuerySpec ) )
				.translate( null, QueryOptions.NONE )
				.getSqlString();
		final int fromIndex = sql.lastIndexOf( " from" );
		final String expression;
		if ( fromIndex != -1 ) {
			expression = sql.substring( "select ".length(), fromIndex );
		}
		else {
			expression = sql.substring( "select ".length() );
		}
		return expression;
	}

	protected String generateSelectSizeString(boolean isIntegerIndexed) {
		String selectValue = isIntegerIndexed ?
				"max(" + getIndexColumnNames()[0] + ") + 1" : // lists, arrays
				"count(" + getElementColumnNames()[0] + ")"; // sets, maps, bags
		return new SimpleSelect( getFactory() )
				.setTableName( getTableName() )
				.addRestriction( getKeyColumnNames() )
				.addWhereToken( sqlWhereString )
				.addColumn( selectValue )
				.toStatementString();
	}

	protected String generateDetectRowByIndexString() {
		if ( !hasIndex() ) {
			return null;
		}
		return new SimpleSelect( getFactory() )
				.setTableName( getTableName() )
				.addRestriction( getKeyColumnNames() )
				.addRestriction( getIndexColumnNames() )
				.addRestriction( indexFormulas )
				.addWhereToken( sqlWhereString )
				.addColumn( "1" )
				.toStatementString();
	}


	protected String generateDetectRowByElementString() {
		return new SimpleSelect( getFactory() )
				.setTableName( getTableName() )
				.addRestriction( getKeyColumnNames() )
				.addRestriction( getElementColumnNames() )
				.addRestriction( elementFormulas )
				.addWhereToken( sqlWhereString )
				.addColumn( "1" )
				.toStatementString();
	}

	@Override
	public String[] getIndexColumnNames() {
		return indexColumnNames;
	}

	@Override
	public String[] getIndexFormulas() {
		return indexFormulas;
	}

	@Override
	public String[] getIndexColumnNames(String alias) {
		return qualify( alias, indexColumnNames, indexFormulaTemplates );
	}

	@Override
	public String[] getElementColumnNames(String alias) {
		return qualify( alias, elementColumnNames, elementFormulaTemplates );
	}

	private static String[] qualify(String alias, String[] columnNames, String[] formulaTemplates) {
		int span = columnNames.length;
		String[] result = new String[span];
		for ( int i = 0; i < span; i++ ) {
			if ( columnNames[i] == null ) {
				result[i] = StringHelper.replace( formulaTemplates[i], Template.TEMPLATE, alias );
			}
			else {
				result[i] = StringHelper.qualify( alias, columnNames[i] );
			}
		}
		return result;
	}

	@Override
	public String[] getElementColumnNames() {
		return elementColumnNames; // TODO: something with formulas...
	}

	@Override
	public String[] getKeyColumnNames() {
		return keyColumnNames;
	}

	@Override
	public boolean hasIndex() {
		return collectionSemantics.getCollectionClassification().isIndexed();
	}

	@Override
	public boolean isLazy() {
		return isLazy;
	}

	@Override
	public boolean isInverse() {
		return isInverse;
	}

	@Override
	public String getTableName() {
		return qualifiedTableName;
	}

	@Override
	public void remove(Object id, SharedSessionContractImplementor session) throws HibernateException {
		getRemoveCoordinator().deleteAllRows( id, session );
	}

	protected boolean isRowDeleteEnabled() {
		return true;
	}

	@Override
	public boolean needsRemove() {
		return !isInverse() && isRowDeleteEnabled();
	}

	protected boolean isRowInsertEnabled() {
		return true;
	}

	public String getOwnerEntityName() {
		return ownerPersister.getEntityName();
	}

	@Override
	public EntityPersister getOwnerEntityPersister() {
		return ownerPersister;
	}

	@Override @Deprecated
	public IdentifierGenerator getIdentifierGenerator() {
		return (IdentifierGenerator) identifierGenerator;
	}

	@Override
	public BeforeExecutionGenerator getGenerator() {
		return identifierGenerator;
	}

	@Override
	public boolean hasOrphanDelete() {
		return hasOrphanDelete;
	}

	@Override
	public Type toType(String propertyName) throws QueryException {
		// todo (PropertyMapping) : simple delegation (aka, easy to remove)
		if ( "index".equals( propertyName ) ) {
			return indexType;
		}
		return elementPropertyMapping.toType( propertyName );
	}

	@Override
	public void applyBaseRestrictions(Consumer<Predicate> predicateConsumer, TableGroup tableGroup, boolean useQualifier, Map<String, Filter> enabledFilters, Set<String> treatAsDeclarations, SqlAstCreationState creationState) {
		applyFilterRestrictions( predicateConsumer, tableGroup, useQualifier, enabledFilters, creationState );
		applyWhereRestrictions( predicateConsumer, tableGroup, useQualifier, creationState );
	}

	@Override
	public void applyWhereRestrictions(
			Consumer<Predicate> predicateConsumer,
			TableGroup tableGroup,
			boolean useQualifier,
			SqlAstCreationState creationState) {
		TableReference tableReference;
		if ( isManyToMany() ) {
			tableReference = tableGroup.getPrimaryTableReference();
		}
		else if ( elementPersister instanceof Joinable ) {
			tableReference = tableGroup.getTableReference( tableGroup.getNavigablePath(), ( (Joinable) elementPersister ).getTableName() );
		}
		else {
			tableReference = tableGroup.getTableReference( tableGroup.getNavigablePath(), qualifiedTableName );
		}

		final String alias;
		if ( tableReference == null ) {
			alias = null;
		}
		else if ( useQualifier && tableReference.getIdentificationVariable() != null ) {
			alias = tableReference.getIdentificationVariable();
		}
		else {
			alias = tableReference.getTableId();
		}

		applyWhereFragments( predicateConsumer, alias, tableGroup, creationState );
	}

	protected void applyWhereFragments(
			Consumer<Predicate> predicateConsumer,
			String alias,
			TableGroup tableGroup,
			SqlAstCreationState astCreationState) {
		applyWhereFragments( predicateConsumer, alias, sqlWhereStringTemplate );
	}

	/**
	 * Applies all defined {@link org.hibernate.annotations.Where}
	 */
	private static void applyWhereFragments(Consumer<Predicate> predicateConsumer, String alias, String template) {
		if ( template == null ) {
			return;
		}

		final String fragment = StringHelper.replace( template, Template.TEMPLATE, alias );
		if ( StringHelper.isEmpty( fragment ) ) {
			return;
		}

		predicateConsumer.accept( new SqlFragmentPredicate( fragment ) );
	}

	@Override
	public void applyFilterRestrictions(Consumer<Predicate> predicateConsumer, TableGroup tableGroup, boolean useQualifier, Map<String, Filter> enabledFilters, SqlAstCreationState creationState) {
		if ( filterHelper != null ) {
			filterHelper.applyEnabledFilters(
					predicateConsumer,
					getFilterAliasGenerator( tableGroup ),
					enabledFilters
			);
		}
	}

	@Override
	public abstract boolean isManyToMany();

	@Override
	public void applyBaseManyToManyRestrictions(Consumer<Predicate> predicateConsumer, TableGroup tableGroup, boolean useQualifier, Map<String, Filter> enabledFilters, Set<String> treatAsDeclarations, SqlAstCreationState creationState) {
		if ( manyToManyFilterHelper == null && manyToManyWhereTemplate == null ) {
			return;
		}


		if ( manyToManyFilterHelper != null ) {
			final FilterAliasGenerator aliasGenerator = elementPersister.getFilterAliasGenerator( tableGroup );
			manyToManyFilterHelper.applyEnabledFilters( predicateConsumer, aliasGenerator, enabledFilters );
		}

		if ( manyToManyWhereString != null ) {
			final TableReference tableReference = tableGroup.resolveTableReference( ( (Joinable) elementPersister ).getTableName() );

			final String alias;
			if ( tableReference == null ) {
				alias = null;
			}
			else if ( useQualifier && tableReference.getIdentificationVariable() != null ) {
				alias = tableReference.getIdentificationVariable();
			}
			else {
				alias = tableReference.getTableId();
			}

			applyWhereFragments( predicateConsumer, alias, manyToManyWhereTemplate );
		}
	}

	@Override
	public String getManyToManyFilterFragment(TableGroup tableGroup, Map<String, Filter> enabledFilters) {
		final StringBuilder fragment = new StringBuilder();

		if ( manyToManyFilterHelper != null ) {
			manyToManyFilterHelper.render( fragment, elementPersister.getFilterAliasGenerator( tableGroup ), enabledFilters );
		}

		if ( manyToManyWhereString != null ) {
			if ( fragment.length() > 0 ) {
				fragment.append( " and " );
			}
			assert elementPersister instanceof Joinable;
			final TableReference tableReference = tableGroup.resolveTableReference( ( (Joinable) elementPersister ).getTableName() );
			fragment.append( StringHelper.replace( manyToManyWhereTemplate, Template.TEMPLATE, tableReference.getIdentificationVariable() ) );
		}

		return fragment.toString();
	}

	private String[] indexFragments;

	@Override
	public String[] toColumns(String propertyName) throws QueryException {
		// todo (PropertyMapping) : simple delegation (aka, easy to remove)
		if ( "index".equals( propertyName ) ) {
			if ( indexFragments == null ) {
				String[] tmp = new String[indexColumnNames.length];
				for ( int i = 0; i < indexColumnNames.length; i++ ) {
					tmp[i] = indexColumnNames[i] == null
							? indexFormulas[i]
							: indexColumnNames[i];
					indexFragments = tmp;
				}
			}
			return indexFragments;
		}

		return elementPropertyMapping.toColumns( propertyName );
	}

	@Override
	public String getName() {
		return getRole();
	}

	@Override
	public EntityPersister getElementPersister() {
		if ( elementPersister == null ) {
			throw new AssertionFailure( "not an association" );
		}
		return elementPersister;
	}

	protected EntityPersister getElementPersisterInternal() {
		return elementPersister;
	}

	@Override
	public boolean isCollection() {
		return true;
	}

	@Override
	public String[] getCollectionSpaces() {
		return spaces;
	}

	@Override
	public void processQueuedOps(PersistentCollection<?> collection, Object key, SharedSessionContractImplementor session) {
		if ( collection.hasQueuedOperations() ) {
			doProcessQueuedOps( collection, key, session );
		}
	}

	protected abstract void doProcessQueuedOps(PersistentCollection<?> collection, Object key, SharedSessionContractImplementor session)
			throws HibernateException;

	@Override @Deprecated
	public CollectionMetadata getCollectionMetadata() {
		return this;
	}

	@Override
	public SessionFactoryImplementor getFactory() {
		return factory;
	}

	@Override
	public String toString() {
		return StringHelper.unqualify( getClass().getName() ) + '(' + navigableRole.getFullPath() + ')';
	}

	@Override
	public boolean isVersioned() {
		return isVersioned && getOwnerEntityPersister().isVersioned();
	}

	// TODO: needed???
	protected SqlExceptionHelper getSQLExceptionHelper() {
		return sqlExceptionHelper;
	}

	@Override
	public CacheEntryStructure getCacheEntryStructure() {
		return cacheEntryStructure;
	}

	@Override
	public boolean isAffectedByEnabledFilters(SharedSessionContractImplementor session) {
		return isAffectedByEnabledFilters( session.getLoadQueryInfluencers() );
	}

	public boolean isSubselectLoadable() {
		return subselectLoadable;
	}

	@Override
	public boolean isMutable() {
		return isMutable;
	}

	@Override
	public String[] getCollectionPropertyColumnAliases(String propertyName, String suffix) {
		String[] rawAliases = collectionPropertyColumnAliases.get( propertyName );

		if ( rawAliases == null ) {
			return null;
		}

		String[] result = new String[rawAliases.length];
		final Alias alias = new Alias( suffix );
		for ( int i = 0; i < rawAliases.length; i++ ) {
			result[i] = alias.toUnquotedAliasString( rawAliases[i] );
		}
		return result;
	}

	// TODO: formulas ?
	public void initCollectionPropertyMap() {

		initCollectionPropertyMap( "key", keyType, keyColumnAliases );
		initCollectionPropertyMap( "element", elementType, elementColumnAliases );
		if ( hasIndex() ) {
			initCollectionPropertyMap( "index", indexType, indexColumnAliases );
		}
		if ( collectionSemantics.getCollectionClassification() == CollectionClassification.ID_BAG ) {
			initCollectionPropertyMap( "id", identifierType, new String[] { identifierColumnAlias } );
		}
	}

	private void initCollectionPropertyMap(String aliasName, Type type, String[] columnAliases) {

		collectionPropertyColumnAliases.put( aliasName, columnAliases );

		//TODO: this code is almost certainly obsolete and can be removed
		if ( type.isComponentType() ) {
			CompositeType ct = (CompositeType) type;
			String[] propertyNames = ct.getPropertyNames();
			for ( int i = 0; i < propertyNames.length; i++ ) {
				String name = propertyNames[i];
				collectionPropertyColumnAliases.put( aliasName + "." + name, new String[] {columnAliases[i]} );
			}
		}

	}

	@Override
	public int getSize(Object key, SharedSessionContractImplementor session) {
		try {
			final JdbcCoordinator jdbcCoordinator = session.getJdbcCoordinator();
			PreparedStatement st = jdbcCoordinator
					.getStatementPreparer()
					.prepareStatement( sqlSelectSizeString );
			try {
				getKeyType().nullSafeSet( st, key, 1, session );
				ResultSet rs = jdbcCoordinator.getResultSetReturn().extract( st );
				try {
					final int baseIndex = Math.max( attributeMapping.getIndexMetadata().getListIndexBase(), 0 );
					return rs.next() ? rs.getInt( 1 ) - baseIndex : 0;
				}
				finally {
					jdbcCoordinator.getLogicalConnection().getResourceRegistry().release( rs, st );
				}
			}
			finally {
				jdbcCoordinator.getLogicalConnection().getResourceRegistry().release( st );
				jdbcCoordinator.afterStatementExecution();
			}
		}
		catch ( SQLException sqle ) {
			throw getSQLExceptionHelper().convert(
					sqle,
					"could not retrieve collection size: " +
							MessageHelper.collectionInfoString( this, key, getFactory() ),
					sqlSelectSizeString
			);
		}
	}

	@Override
	public boolean indexExists(Object key, Object index, SharedSessionContractImplementor session) {
		return exists( key, incrementIndexByBase( index ), getIndexType(), sqlDetectRowByIndexString, session );
	}

	@Override
	public boolean elementExists(Object key, Object element, SharedSessionContractImplementor session) {
		return exists( key, element, getElementType(), sqlDetectRowByElementString, session );
	}

	private boolean exists(Object key, Object indexOrElement, Type indexOrElementType, String sql, SharedSessionContractImplementor session) {
		try {
			final JdbcCoordinator jdbcCoordinator = session.getJdbcCoordinator();
			PreparedStatement st = jdbcCoordinator
					.getStatementPreparer()
					.prepareStatement( sql );
			try {
				getKeyType().nullSafeSet( st, key, 1, session );
				indexOrElementType.nullSafeSet( st, indexOrElement, keyColumnNames.length + 1, session );
				ResultSet rs = jdbcCoordinator.getResultSetReturn().extract( st );
				try {
					return rs.next();
				}
				finally {
					jdbcCoordinator.getLogicalConnection().getResourceRegistry().release( rs, st );
				}
			}
			catch ( TransientObjectException e ) {
				return false;
			}
			finally {
				jdbcCoordinator.getLogicalConnection().getResourceRegistry().release( st );
				jdbcCoordinator.afterStatementExecution();
			}
		}
		catch ( SQLException sqle ) {
			throw getSQLExceptionHelper().convert(
					sqle,
					"could not check row existence: " +
							MessageHelper.collectionInfoString( this, key, getFactory() ),
					sqlSelectSizeString
			);
		}
	}

	@Override
	public Object getElementByIndex(Object key, Object index, SharedSessionContractImplementor session, Object owner) {
		return collectionElementLoaderByIndex.load( key, index, session );
	}

	@Override
	public boolean isExtraLazy() {
		return isExtraLazy;
	}

	protected Dialect getDialect() {
		return dialect;
	}

	@Override
	public int getBatchSize() {
		return batchSize;
	}

	@Override
	public String getMappedByProperty() {
		return mappedByProperty;
	}

	public abstract FilterAliasGenerator getFilterAliasGenerator(String rootAlias);

	public abstract FilterAliasGenerator getFilterAliasGenerator(TableGroup tableGroup);

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// "mapping model"

	/**
	 * Allows injection of the corresponding {@linkplain PluralAttributeMapping plural-attribute mapping}.
	 *
	 * @implNote Helps solve the chicken-egg problem of which to create first.  Ultimately we could
	 * make this work in a similar fashion to how this works in the relationship between
	 * {@link org.hibernate.metamodel.mapping.EmbeddableMappingType} and {@link EmbeddableValuedModelPart}.
	 */
	@Override
	public void injectAttributeMapping(PluralAttributeMapping attributeMapping) {
		this.attributeMapping = attributeMapping;
	}

	@Override
	public PluralAttributeMapping getAttributeMapping() {
		return attributeMapping;
	}

	@Override
	public boolean isAffectedByEnabledFilters(LoadQueryInfluencers influencers) {
		if ( influencers.hasEnabledFilters() ) {
			final Map<String, Filter> enabledFilters = influencers.getEnabledFilters();
			return ( filterHelper != null && filterHelper.isAffectedBy( enabledFilters ) )
					|| ( manyToManyFilterHelper != null && manyToManyFilterHelper.isAffectedBy( enabledFilters ) );
		}

		return false;
	}

	@Override
	public boolean isAffectedByEntityGraph(LoadQueryInfluencers influencers) {
		// todo (6.0) : anything to do here?
		return false;
	}

	@Override
	public boolean isAffectedByEnabledFetchProfiles(LoadQueryInfluencers influencers) {
		if ( influencers.hasEnabledFetchProfiles() ) {
			for ( String enabledFetchProfileName : influencers.getEnabledFetchProfileNames() ) {
				final FetchProfile fetchProfile = getFactory().getFetchProfile( enabledFetchProfileName );
				final Fetch fetch = fetchProfile.getFetchByRole( getRole() );
				if ( fetch != null && fetch.getStyle() == Fetch.Style.JOIN ) {
					return true;
				}
			}
		}

		return false;
	}

	@Override
	public CollectionSemantics<?,?> getCollectionSemantics() {
		return collectionSemantics;
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// CollectionMutationTarget

	@Override
	public PluralAttributeMapping getTargetPart() {
		return attributeMapping;
	}

	@Override
	public String getIdentifierTableName() {
		return tableMapping.getTableName();
	}

	@Override
	public CollectionTableMapping getCollectionTableMapping() {
		return tableMapping;
	}

	@Override
	public boolean hasPhysicalIndexColumn() {
		return hasIndex() && !indexContainsFormula;
	}

	@Override
	public void forEachMutableTable(Consumer<CollectionTableMapping> consumer) {
		consumer.accept( tableMapping );
	}

	@Override
	public void forEachMutableTableReverse(Consumer<CollectionTableMapping> consumer) {
		consumer.accept( tableMapping );
	}

	private static CollectionTableMapping buildCollectionTableMapping(
			Collection collectionBootDescriptor,
			String qualifiedTableName,
			String[] spaces) {
		return new CollectionTableMapping(
				qualifiedTableName,
				spaces,
				!collectionBootDescriptor.isOneToMany(),
				collectionBootDescriptor.isInverse(),
				new MutationDetails(
						MutationType.INSERT,
						determineExpectation(
								collectionBootDescriptor.getCustomSQLInsertCheckStyle(),
								collectionBootDescriptor.getCustomSQLInsert(),
								collectionBootDescriptor.isCustomInsertCallable()
						),
						collectionBootDescriptor.getCustomSQLInsert(),
						collectionBootDescriptor.isCustomInsertCallable()
				),
				new MutationDetails(
						MutationType.UPDATE,
						determineExpectation(
								collectionBootDescriptor.getCustomSQLUpdateCheckStyle(),
								collectionBootDescriptor.getCustomSQLUpdate(),
								collectionBootDescriptor.isCustomUpdateCallable()
						),
						collectionBootDescriptor.getCustomSQLUpdate(),
						collectionBootDescriptor.isCustomUpdateCallable()
				),
				collectionBootDescriptor.getKey().isCascadeDeleteEnabled(),
				new MutationDetails(
						MutationType.DELETE,
						determineExpectation(
								collectionBootDescriptor.getCustomSQLDeleteAllCheckStyle(),
								collectionBootDescriptor.getCustomSQLDeleteAll(),
								collectionBootDescriptor.isCustomDeleteAllCallable(),
								Expectations.NONE
						),
						collectionBootDescriptor.getCustomSQLDeleteAll(),
						collectionBootDescriptor.isCustomDeleteAllCallable()
				),
				new MutationDetails(
						MutationType.DELETE,
						determineExpectation(
								collectionBootDescriptor.getCustomSQLDeleteCheckStyle(),
								collectionBootDescriptor.getCustomSQLDelete(),
								collectionBootDescriptor.isCustomDeleteCallable()
						),
						collectionBootDescriptor.getCustomSQLDelete(),
						collectionBootDescriptor.isCustomDeleteCallable()
				)
		);
	}

	private static Expectation determineExpectation(
			ExecuteUpdateResultCheckStyle explicitStyle,
			String customSql,
			boolean customSqlCallable,
			Expectation fallback) {
		if ( explicitStyle != null ) {
			return Expectations.appropriateExpectation( explicitStyle );
		}

		if ( customSql == null ) {
			return fallback;
		}

		return Expectations.appropriateExpectation(
				ExecuteUpdateResultCheckStyle.determineDefault( customSql, customSqlCallable )
		);
	}

	private static Expectation determineExpectation(
			ExecuteUpdateResultCheckStyle explicitStyle,
			String customSql,
			boolean customSqlCallable) {
		return determineExpectation( explicitStyle, customSql, customSqlCallable, Expectations.BASIC );
	}

	protected JdbcMutationOperation buildDeleteAllOperation(MutatingTableReference tableReference) {
		if ( tableMapping.getDeleteDetails().getCustomSql() != null ) {
			return buildCustomSqlDeleteAllOperation( tableReference );
		}

		return buildGeneratedDeleteAllOperation( tableReference );
	}

	private JdbcDeleteMutation buildCustomSqlDeleteAllOperation(MutatingTableReference tableReference) {
		final PluralAttributeMapping attributeMapping = getAttributeMapping();
		final ForeignKeyDescriptor keyDescriptor = attributeMapping.getKeyDescriptor();

		final ColumnValueParameterList parameterBinders = new ColumnValueParameterList(
				tableReference,
				ParameterUsage.RESTRICT,
				keyDescriptor.getJdbcTypeCount()
		);
		keyDescriptor.getKeyPart().forEachSelectable( parameterBinders );

		final TableMapping tableMapping = tableReference.getTableMapping();
		return new JdbcDeleteMutation(
				tableMapping,
				this,
				tableMapping.getDeleteDetails().getCustomSql(),
				tableMapping.getDeleteDetails().isCallable(),
				tableMapping.getDeleteDetails().getExpectation(),
				parameterBinders
		);
	}

	private JdbcMutationOperation buildGeneratedDeleteAllOperation(MutatingTableReference tableReference) {
		final RestrictedTableMutation<JdbcMutationOperation> sqlAst = generateDeleteAllAst( tableReference );

		final SqlAstTranslator<JdbcMutationOperation> translator = getFactory().getJdbcServices()
				.getDialect()
				.getSqlAstTranslatorFactory()
				.buildModelMutationTranslator( sqlAst, getFactory() );

		return translator.translate( null, MutationQueryOptions.INSTANCE );
	}

	public RestrictedTableMutation<JdbcMutationOperation> generateDeleteAllAst(MutatingTableReference tableReference) {
		assert getAttributeMapping() != null;

		final ForeignKeyDescriptor fkDescriptor = getAttributeMapping().getKeyDescriptor();
		assert fkDescriptor != null;

		final int keyColumnCount = fkDescriptor.getJdbcTypeCount();
		final ColumnValueParameterList parameterBinders = new ColumnValueParameterList(
				tableReference,
				ParameterUsage.RESTRICT,
				keyColumnCount
		);
		final java.util.List<ColumnValueBinding> keyRestrictionBindings = arrayList( keyColumnCount );
		fkDescriptor.getKeyPart().forEachSelectable( parameterBinders );
		for ( ColumnValueParameter columnValueParameter : parameterBinders ) {
			final ColumnReference columnReference = columnValueParameter.getColumnReference();
			keyRestrictionBindings.add(
					new ColumnValueBinding(
							columnReference,
							new ColumnWriteFragment(
									"?",
									columnValueParameter,
									columnReference.getJdbcMapping()
							)
					)
			);
		}

		//noinspection unchecked,rawtypes
		return (RestrictedTableMutation) new TableDeleteStandard(
				tableReference,
				this,
				"one-shot delete for " + getRolePath(),
				keyRestrictionBindings,
				Collections.emptyList(),
				parameterBinders
		);
	}



	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Types (the methods are already deprecated on CollectionPersister)
	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	@Deprecated private final CollectionType collectionType;
	@Deprecated private final Type keyType;
	@Deprecated private final Type identifierType;
	@Deprecated private final Type indexType;
	@Deprecated protected final Type elementType;

	public CollectionType getCollectionType() {
		return collectionType;
	}

	@Override
	public Type getKeyType() {
		return keyType;
	}

	@Override
	public Type getIdentifierType() {
		return identifierType;
	}

	@Override
	public Type getIndexType() {
		return indexType;
	}

	@Override
	public Type getElementType() {
		return elementType;
	}



	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// State related to this we handle differently in 6+.  In other words, state
	// that is no longer needed
	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	@Deprecated protected final String[] keyColumnAliases;
	@Deprecated private final String identifierColumnAlias;
	@Deprecated protected final String[] indexColumnAliases;
	@Deprecated protected final String[] elementColumnAliases;
	@Deprecated private final Map<String,String[]> collectionPropertyColumnAliases = new HashMap<>();

	@Override
	public String[] getKeyColumnAliases(String suffix) {
		return new Alias( suffix ).toAliasStrings( keyColumnAliases );
	}

	@Override
	public String[] getElementColumnAliases(String suffix) {
		return new Alias( suffix ).toAliasStrings( elementColumnAliases );
	}

	@Override
	public String[] getIndexColumnAliases(String suffix) {
		if ( hasIndex() ) {
			return new Alias( suffix ).toAliasStrings( indexColumnAliases );
		}
		else {
			return null;
		}
	}

	@Override
	public String getIdentifierColumnAlias(String suffix) {
		if ( collectionSemantics.getCollectionClassification() == CollectionClassification.ID_BAG ) {
			return new Alias( suffix ).toAliasString( identifierColumnAlias );
		}
		else {
			return null;
		}
	}
}
