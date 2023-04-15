/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.spi;

import java.sql.Types;
import java.time.Instant;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import jakarta.persistence.CacheRetrieveMode;
import jakarta.persistence.CacheStoreMode;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Parameter;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import jakarta.persistence.criteria.CompoundSelection;

import org.hibernate.CacheMode;
import org.hibernate.FlushMode;
import org.hibernate.HibernateException;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.NonUniqueResultException;
import org.hibernate.ScrollMode;
import org.hibernate.TypeMismatchException;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.graph.spi.AppliedGraph;
import org.hibernate.jpa.internal.util.LockModeTypeHelper;
import org.hibernate.metamodel.model.domain.BasicDomainType;
import org.hibernate.metamodel.model.domain.DomainType;
import org.hibernate.query.BindableType;
import org.hibernate.query.IllegalQueryOperationException;
import org.hibernate.query.QueryParameter;
import org.hibernate.query.QueryTypeMismatchException;
import org.hibernate.query.SelectionQuery;
import org.hibernate.query.criteria.JpaSelection;
import org.hibernate.query.internal.ScrollableResultsIterator;
import org.hibernate.query.named.NamedQueryMemento;
import org.hibernate.query.sqm.SqmExpressible;
import org.hibernate.query.sqm.SqmPathSource;
import org.hibernate.query.sqm.spi.NamedSqmQueryMemento;
import org.hibernate.query.sqm.tree.SqmStatement;
import org.hibernate.query.sqm.tree.expression.SqmParameter;
import org.hibernate.query.sqm.tree.from.SqmRoot;
import org.hibernate.query.sqm.tree.select.SqmQueryGroup;
import org.hibernate.query.sqm.tree.select.SqmQueryPart;
import org.hibernate.query.sqm.tree.select.SqmQuerySpec;
import org.hibernate.query.sqm.tree.select.SqmSelectStatement;
import org.hibernate.query.sqm.tree.select.SqmSelection;
import org.hibernate.sql.exec.internal.CallbackImpl;
import org.hibernate.sql.exec.spi.Callback;
import org.hibernate.sql.results.internal.TupleMetadata;
import org.hibernate.type.BasicType;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.spi.PrimitiveJavaType;
import org.hibernate.type.descriptor.jdbc.JdbcType;

import static org.hibernate.CacheMode.fromJpaModes;
import static org.hibernate.cfg.AvailableSettings.JAKARTA_SHARED_CACHE_RETRIEVE_MODE;
import static org.hibernate.cfg.AvailableSettings.JAKARTA_SHARED_CACHE_STORE_MODE;
import static org.hibernate.cfg.AvailableSettings.JPA_SHARED_CACHE_RETRIEVE_MODE;
import static org.hibernate.cfg.AvailableSettings.JPA_SHARED_CACHE_STORE_MODE;
import static org.hibernate.jpa.QueryHints.HINT_CACHEABLE;
import static org.hibernate.jpa.QueryHints.HINT_CACHE_MODE;
import static org.hibernate.jpa.QueryHints.HINT_CACHE_REGION;
import static org.hibernate.jpa.QueryHints.HINT_FETCH_SIZE;
import static org.hibernate.jpa.QueryHints.HINT_FOLLOW_ON_LOCKING;
import static org.hibernate.jpa.QueryHints.HINT_READONLY;

/**
 * @author Steve Ebersole
 */
public abstract class AbstractSelectionQuery<R>
		extends AbstractCommonQueryContract
		implements SelectionQuery<R>, DomainQueryExecutionContext {
	/**
	 * The value used for {@link #getQueryString} for Criteria-based queries
	 */
	public static final String CRITERIA_HQL_STRING = "<criteria>";

	private Callback callback;

	public AbstractSelectionQuery(SharedSessionContractImplementor session) {
		super( session );
	}

	protected TupleMetadata buildTupleMetadata(SqmStatement<?> statement, Class<R> resultType) {
		if ( resultType != null && Tuple.class.isAssignableFrom( resultType ) ) {
			final List<SqmSelection<?>> selections = ( (SqmSelectStatement<?>) statement ).getQueryPart()
					.getFirstQuerySpec()
					.getSelectClause()
					.getSelections();
			// resultType is Tuple..
			if ( getQueryOptions().getTupleTransformer() == null ) {
				final Map<TupleElement<?>, Integer> tupleElementMap;
				if ( selections.size() == 1 && selections.get( 0 ).getSelectableNode() instanceof CompoundSelection<?> ) {
					final List<? extends JpaSelection<?>> selectionItems = selections.get( 0 )
							.getSelectableNode()
							.getSelectionItems();
					tupleElementMap = new IdentityHashMap<>( selectionItems.size() );
					for ( int i = 0; i < selectionItems.size(); i++ ) {
						tupleElementMap.put( selectionItems.get( i ), i );
					}
				}
				else {
					tupleElementMap = new IdentityHashMap<>( selections.size() );
					for ( int i = 0; i < selections.size(); i++ ) {
						final SqmSelection<?> selection = selections.get( i );
						tupleElementMap.put( selection.getSelectableNode(), i );
					}
				}
				return new TupleMetadata( tupleElementMap );
			}

			throw new IllegalArgumentException(
					"Illegal combination of Tuple resultType and (non-JpaTupleBuilder) TupleTransformer : " +
							getQueryOptions().getTupleTransformer()
			);
		}
		return null;
	}

	protected void applyOptions(NamedSqmQueryMemento memento) {
		applyOptions( (NamedQueryMemento) memento );

		if ( memento.getFirstResult() != null ) {
			setFirstResult( memento.getFirstResult() );
		}

		if ( memento.getMaxResults() != null ) {
			setMaxResults( memento.getMaxResults() );
		}

		if ( memento.getParameterTypes() != null ) {
			for ( Map.Entry<String, String> entry : memento.getParameterTypes().entrySet() ) {
				final QueryParameterImplementor<?> parameter = getParameterMetadata().getQueryParameter( entry.getKey() );
				final BasicType<?> type = getSessionFactory().getTypeConfiguration()
						.getBasicTypeRegistry()
						.getRegisteredType( entry.getValue() );
				parameter.applyAnticipatedType( type );
			}
		}
	}

	protected void applyOptions(NamedQueryMemento memento) {
		if ( memento.getHints() != null ) {
			memento.getHints().forEach( this::applyHint );
		}

		if ( memento.getCacheable() != null ) {
			setCacheable( memento.getCacheable() );
		}

		if ( memento.getCacheRegion() != null ) {
			setCacheRegion( memento.getCacheRegion() );
		}

		if ( memento.getCacheMode() != null ) {
			setCacheMode( memento.getCacheMode() );
		}

		if ( memento.getFlushMode() != null ) {
			setHibernateFlushMode( memento.getFlushMode() );
		}

		if ( memento.getReadOnly() != null ) {
			setReadOnly( memento.getReadOnly() );
		}

		if ( memento.getTimeout() != null ) {
			setTimeout( memento.getTimeout() );
		}

		if ( memento.getFetchSize() != null ) {
			setFetchSize( memento.getFetchSize() );
		}

		if ( memento.getComment() != null ) {
			setComment( memento.getComment() );
		}
	}

	protected abstract String getQueryString();

	/**
	 * Used during handling of Criteria queries
	 */
	protected void visitQueryReturnType(
			SqmQueryPart<R> queryPart,
			Class<R> resultType,
			SessionFactoryImplementor factory) {
		assert getQueryString().equals( CRITERIA_HQL_STRING );

		if ( queryPart instanceof SqmQuerySpec<?> ) {
			final SqmQuerySpec<R> sqmQuerySpec = (SqmQuerySpec<R>) queryPart;
			final List<SqmSelection<?>> sqmSelections = sqmQuerySpec.getSelectClause().getSelections();

			if ( sqmSelections == null || sqmSelections.isEmpty() ) {
				// make sure there is at least one root
				final List<SqmRoot<?>> sqmRoots = sqmQuerySpec.getFromClause().getRoots();
				if ( sqmRoots == null || sqmRoots.isEmpty() ) {
					throw new IllegalArgumentException( "Criteria did not define any query roots" );
				}
				// if there is a single root, use that as the selection
				if ( sqmRoots.size() == 1 ) {
					final SqmRoot<?> sqmRoot = sqmRoots.get( 0 );
					sqmQuerySpec.getSelectClause().add( sqmRoot, null );
				}
				else {
					throw new IllegalArgumentException(  );
				}
			}

			if ( resultType != null ) {
				checkQueryReturnType( sqmQuerySpec, resultType, factory );
			}
		}
		else {
			final SqmQueryGroup<R> queryGroup = (SqmQueryGroup<R>) queryPart;
			for ( SqmQueryPart<R> sqmQueryPart : queryGroup.getQueryParts() ) {
				visitQueryReturnType( sqmQueryPart, resultType, factory );
			}
		}
	}

	protected static <T> void checkQueryReturnType(
			SqmQuerySpec<T> querySpec,
			Class<T> resultClass,
			SessionFactoryImplementor sessionFactory) {
		if ( resultClass == null || resultClass == Object.class ) {
			// nothing to check
			return;
		}

		final List<SqmSelection<?>> selections = querySpec.getSelectClause().getSelections();

		if ( resultClass.isArray() ) {
			// todo (6.0) : implement
		}
		else if ( Tuple.class.isAssignableFrom( resultClass ) ) {
			// todo (6.0) : implement
		}
		else {
			final boolean jpaQueryComplianceEnabled = sessionFactory.getSessionFactoryOptions()
					.getJpaCompliance()
					.isJpaQueryComplianceEnabled();
			if ( selections.size() != 1 ) {
				final String errorMessage = "Query result-type error - multiple selections: use Tuple or array";

				if ( jpaQueryComplianceEnabled ) {
					throw new IllegalArgumentException( errorMessage );
				}
				else {
					throw new QueryTypeMismatchException( errorMessage );
				}
			}

			final SqmSelection<?> sqmSelection = selections.get( 0 );

			if ( sqmSelection.getSelectableNode() instanceof SqmParameter ) {
				final SqmParameter<?> sqmParameter = (SqmParameter<?>) sqmSelection.getSelectableNode();

				// we may not yet know a selection type
				if ( sqmParameter.getNodeType() == null || sqmParameter.getNodeType().getExpressibleJavaType() == null ) {
					// we can't verify the result type up front
					return;
				}
			}

			if ( jpaQueryComplianceEnabled ) {
				return;
			}
			verifyResultType( resultClass, sqmSelection.getNodeType(), sessionFactory );
		}
	}

	protected static <T> void verifyResultType(
			Class<T> resultClass,
			SqmExpressible<?> sqmExpressible,
			SessionFactoryImplementor sessionFactory) {
		assert sqmExpressible != null;
		final JavaType<?> expressibleJavaType = sqmExpressible.getExpressibleJavaType();
		assert expressibleJavaType != null;
		final Class<?> javaTypeClass = expressibleJavaType.getJavaTypeClass();
		if ( !resultClass.isAssignableFrom( javaTypeClass ) ) {
			if ( expressibleJavaType instanceof PrimitiveJavaType ) {
				if ( ( (PrimitiveJavaType) expressibleJavaType ).getPrimitiveClass() == resultClass ) {
					return;
				}
				throwQueryTypeMismatchException( resultClass, sqmExpressible );
			}
			// Special case for date because we always report java.util.Date as expression type
			// But the expected resultClass could be a subtype of that, so we need to check the JdbcType
			if ( javaTypeClass == Date.class ) {
				JdbcType jdbcType = null;
				if ( sqmExpressible instanceof BasicDomainType<?> ) {
					jdbcType = ( (BasicDomainType<?>) sqmExpressible).getJdbcType();
				}
				else if ( sqmExpressible instanceof SqmPathSource<?> ) {
					final DomainType<?> domainType = ( (SqmPathSource<?>) sqmExpressible).getSqmPathType();
					if ( domainType instanceof BasicDomainType<?> ) {
						jdbcType = ( (BasicDomainType<?>) domainType ).getJdbcType();
					}
				}
				if ( jdbcType != null ) {
					switch ( jdbcType.getDefaultSqlTypeCode() ) {
						case Types.DATE:
							if ( resultClass.isAssignableFrom( java.sql.Date.class ) ) {
								return;
							}
							break;
						case Types.TIME:
							if ( resultClass.isAssignableFrom( java.sql.Time.class ) ) {
								return;
							}
							break;
						case Types.TIMESTAMP:
							if ( resultClass.isAssignableFrom( java.sql.Timestamp.class ) ) {
								return;
							}
							break;
					}
				}
			}
			throwQueryTypeMismatchException( resultClass, sqmExpressible );
		}
	}

	private static <T> void throwQueryTypeMismatchException(Class<T> resultClass, SqmExpressible<?> sqmExpressible) {
		final String errorMessage = String.format(
				"Specified result type [%s] did not match Query selection type [%s] - multiple selections: use Tuple or array",
				resultClass.getName(),
				sqmExpressible.getExpressibleJavaType().getJavaType().getTypeName()
		);
		throw new QueryTypeMismatchException( errorMessage );
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// execution

	private FlushMode sessionFlushMode;
	private CacheMode sessionCacheMode;

	@Override
	public List<R> list() {
		beforeQuery();
		boolean success = false;
		try {
			final List<R> result = doList();
			success = true;
			return result;
		}
		catch (IllegalQueryOperationException e) {
			throw new IllegalStateException( e );
		}
		catch (TypeMismatchException e) {
			throw new IllegalArgumentException( e );
		}
		catch (HibernateException he) {
			throw getSession().getExceptionConverter().convert( he, getQueryOptions().getLockOptions() );
		}
		finally {
			afterQuery( success );
		}
	}

	protected void beforeQuery() {
		getQueryParameterBindings().validate();

		getSession().prepareForQueryExecution(false);
		prepareForExecution();

		assert sessionFlushMode == null;
		assert sessionCacheMode == null;

		final FlushMode effectiveFlushMode = getHibernateFlushMode();
		if ( effectiveFlushMode != null ) {
			sessionFlushMode = getSession().getHibernateFlushMode();
			getSession().setHibernateFlushMode( effectiveFlushMode );
		}

		final CacheMode effectiveCacheMode = getCacheMode();
		if ( effectiveCacheMode != null ) {
			sessionCacheMode = getSession().getCacheMode();
			getSession().setCacheMode( effectiveCacheMode );
		}
	}

	protected abstract void prepareForExecution();

	protected void afterQuery(boolean success) {
		if ( sessionFlushMode != null ) {
			getSession().setHibernateFlushMode( sessionFlushMode );
			sessionFlushMode = null;
		}
		if ( sessionCacheMode != null ) {
			getSession().setCacheMode( sessionCacheMode );
			sessionCacheMode = null;
		}
		if ( !getSession().isTransactionInProgress() ) {
			getSession().getJdbcCoordinator().getLogicalConnection().afterTransaction();
		}
		getSession().afterOperation( success );
	}

	protected boolean requiresTxn(LockMode lockMode) {
		return lockMode != null && lockMode.greaterThan( LockMode.READ );
	}

	protected abstract List<R> doList();

	@Override
	public ScrollableResultsImplementor<R> scroll() {
		return scroll( getSession().getFactory().getJdbcServices().getJdbcEnvironment().getDialect().defaultScrollMode() );
	}

	@Override
	public ScrollableResultsImplementor<R> scroll(ScrollMode scrollMode) {
		return doScroll( scrollMode );
	}

	protected abstract ScrollableResultsImplementor<R> doScroll(ScrollMode scrollMode);

	@SuppressWarnings( {"unchecked", "rawtypes"} )
	@Override
	public Stream stream() {
		final ScrollableResultsImplementor scrollableResults = scroll( ScrollMode.FORWARD_ONLY );
		final ScrollableResultsIterator iterator = new ScrollableResultsIterator<>( scrollableResults );
		final Spliterator spliterator = Spliterators.spliteratorUnknownSize( iterator, Spliterator.NONNULL );

		final Stream stream = StreamSupport.stream( spliterator, false );
		return (Stream) stream.onClose( scrollableResults::close );
	}

	@Override
	public R uniqueResult() {
		return uniqueElement( list() );
	}

	@Override
	public R getSingleResult() {
		try {
			final List<R> list = list();
			if ( list.isEmpty() ) {
				throw new NoResultException(
						String.format( "No result found for query [%s]", getQueryString() )
				);
			}
			return uniqueElement( list );
		}
		catch ( HibernateException e ) {
			throw getSession().getExceptionConverter().convert( e, getQueryOptions().getLockOptions() );
		}
	}

	protected static <T> T uniqueElement(List<T> list) throws NonUniqueResultException {
		int size = list.size();
		if ( size == 0 ) {
			return null;
		}
		final T first = list.get( 0 );
		// todo (6.0) : add a setting here to control whether to perform this validation or not
		for ( int i = 1; i < size; i++ ) {
			if ( list.get( i ) != first ) {
				throw new NonUniqueResultException( list.size() );
			}
		}
		return first;
	}

	@Override
	public Optional<R> uniqueResultOptional() {
		return Optional.ofNullable( uniqueResult() );
	}

	@Override
	public R getSingleResultOrNull() {
		try {
			return uniqueElement( list() );
		}
		catch ( HibernateException e ) {
			throw getSession().getExceptionConverter().convert( e, getLockOptions() );
		}
	}

	protected static boolean hasLimit(SqmSelectStatement<?> sqm, MutableQueryOptions queryOptions) {
		return queryOptions.hasLimit() || sqm.getFetch() != null || sqm.getOffset() != null;
	}

	protected static boolean hasAppliedGraph(MutableQueryOptions queryOptions) {
		return queryOptions.getAppliedGraph() != null
				&& queryOptions.getAppliedGraph().getSemantic() != null;
	}



	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// DomainQueryExecutionContext

	@Override
	public Callback getCallback() {
		if ( callback == null ) {
			callback = new CallbackImpl();
		}
		return callback;
	}

	protected void resetCallback() {
		callback = null;
	}


	@Override
	public FlushModeType getFlushMode() {
		return getQueryOptions().getFlushMode().toJpaFlushMode();
	}

	@Override
	public SelectionQuery<R> setFlushMode(FlushModeType flushMode) {
		getQueryOptions().setFlushMode( FlushMode.fromJpaFlushMode( flushMode ) );
		return this;
	}

	@Override
	public SelectionQuery<R> setMaxResults(int maxResult) {
		super.applyMaxResults( maxResult );
		return this;
	}

	@Override
	public SelectionQuery<R> setFirstResult(int startPosition) {
		getSession().checkOpen();

		if ( startPosition < 0 ) {
			throw new IllegalArgumentException( "first-result value cannot be negative : " + startPosition );
		}

		getQueryOptions().getLimit().setFirstRow( startPosition );

		return this;
	}

	@Override
	public SelectionQuery<R> setHint(String hintName, Object value) {
		super.setHint( hintName, value );
		return this;
	}


	@Override
	public LockOptions getLockOptions() {
		return getQueryOptions().getLockOptions();
	}

	@Override
	public LockModeType getLockMode() {
		return LockModeTypeHelper.getLockModeType( getHibernateLockMode() );
	}

	/**
	 * Specify the root LockModeType for the query
	 *
	 * @see #setHibernateLockMode
	 */
	@Override
	public SelectionQuery<R> setLockMode(LockModeType lockMode) {
		setHibernateLockMode( LockModeTypeHelper.getLockMode( lockMode ) );
		return this;
	}

	@Override
	public SelectionQuery<R> setLockMode(String alias, LockMode lockMode) {
		getQueryOptions().getLockOptions().setAliasSpecificLockMode( alias, lockMode );
		return this;
	}

	/**
	 * Get the root LockMode for the query
	 */
	@Override
	public LockMode getHibernateLockMode() {
		return getLockOptions().getLockMode();
	}

	/**
	 * Specify the root LockMode for the query
	 */
	@Override
	public SelectionQuery<R> setHibernateLockMode(LockMode lockMode) {
		getLockOptions().setLockMode( lockMode );
		return this;
	}

	/**
	 * Specify a LockMode to apply to a specific alias defined in the query
	 *
	 * @deprecated use {{@link #setLockMode(String, LockMode)}}
	 */
	@Override @Deprecated
	public SelectionQuery<R> setAliasSpecificLockMode(String alias, LockMode lockMode) {
		getLockOptions().setAliasSpecificLockMode( alias, lockMode );
		return this;
	}

	/**
	 * Specifies whether follow-on locking should be applied?
	 */
	public SelectionQuery<R> setFollowOnLocking(boolean enable) {
		getLockOptions().setFollowOnLocking( enable );
		return this;
	}

	protected void collectHints(Map<String, Object> hints) {
		super.collectHints( hints );

		if ( isReadOnly() ) {
			hints.put( HINT_READONLY, true );
		}

		putIfNotNull( hints, HINT_FETCH_SIZE, getFetchSize() );

		if ( isCacheable() ) {
			hints.put( HINT_CACHEABLE, true );
			putIfNotNull( hints, HINT_CACHE_REGION, getCacheRegion() );

			putIfNotNull( hints, HINT_CACHE_MODE, getCacheMode() );
			putIfNotNull( hints, JAKARTA_SHARED_CACHE_RETRIEVE_MODE, getQueryOptions().getCacheRetrieveMode() );
			putIfNotNull( hints, JAKARTA_SHARED_CACHE_STORE_MODE, getQueryOptions().getCacheStoreMode() );
			//noinspection deprecation
			putIfNotNull( hints, JPA_SHARED_CACHE_RETRIEVE_MODE, getQueryOptions().getCacheRetrieveMode() );
			//noinspection deprecation
			putIfNotNull( hints, JPA_SHARED_CACHE_STORE_MODE, getQueryOptions().getCacheStoreMode() );
		}

		final AppliedGraph appliedGraph = getQueryOptions().getAppliedGraph();
		if ( appliedGraph != null && appliedGraph.getSemantic() != null ) {
			hints.put( appliedGraph.getSemantic().getJakartaHintName(), appliedGraph );
			hints.put( appliedGraph.getSemantic().getJpaHintName(), appliedGraph );
		}

		putIfNotNull( hints, HINT_FOLLOW_ON_LOCKING, getQueryOptions().getLockOptions().getFollowOnLocking() );
	}

	@Override
	public Integer getFetchSize() {
		return getQueryOptions().getFetchSize();
	}

	@Override
	public SelectionQuery<R> setFetchSize(int fetchSize) {
		getQueryOptions().setFetchSize( fetchSize );
		return this;
	}

	@Override
	public boolean isReadOnly() {
		return getQueryOptions().isReadOnly() == null
				? getSession().isDefaultReadOnly()
				: getQueryOptions().isReadOnly();
	}

	@Override
	public SelectionQuery<R> setReadOnly(boolean readOnly) {
		getQueryOptions().setReadOnly( readOnly );
		return this;
	}
	@Override
	public CacheMode getCacheMode() {
		return getQueryOptions().getCacheMode();
	}

	@Override
	public CacheStoreMode getCacheStoreMode() {
		return getCacheMode().getJpaStoreMode();
	}

	@Override
	public CacheRetrieveMode getCacheRetrieveMode() {
		return getCacheMode().getJpaRetrieveMode();
	}

	@Override
	public SelectionQuery<R> setCacheMode(CacheMode cacheMode) {
		getQueryOptions().setCacheMode( cacheMode );
		return this;
	}

	@Override
	public SelectionQuery<R> setCacheRetrieveMode(CacheRetrieveMode cacheRetrieveMode) {
		return setCacheMode( fromJpaModes( cacheRetrieveMode, getQueryOptions().getCacheMode().getJpaStoreMode() ) );
	}

	@Override
	public SelectionQuery<R> setCacheStoreMode(CacheStoreMode cacheStoreMode) {
		return setCacheMode( fromJpaModes( getQueryOptions().getCacheMode().getJpaRetrieveMode(), cacheStoreMode ) );
	}

	@Override
	public boolean isCacheable() {
		return getQueryOptions().isResultCachingEnabled() == Boolean.TRUE;
	}

	@Override
	public SelectionQuery<R> setCacheable(boolean cacheable) {
		getQueryOptions().setResultCachingEnabled( cacheable );
		return this;
	}

	@Override
	public String getCacheRegion() {
		return getQueryOptions().getResultCacheRegionName();
	}

	@Override
	public SelectionQuery<R> setCacheRegion(String regionName) {
		getQueryOptions().setResultCacheRegionName( regionName );
		return this;
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// covariance

	@Override
	public SelectionQuery<R> setHibernateFlushMode(FlushMode flushMode) {
		super.setHibernateFlushMode( flushMode );
		return this;
	}

	@Override
	public SelectionQuery<R> setTimeout(int timeout) {
		super.setTimeout( timeout );
		return this;
	}


	@Override
	public SelectionQuery<R> setParameter(String name, Object value) {
		super.setParameter( name, value );
		return this;
	}

	@Override
	public <P> SelectionQuery<R> setParameter(String name, P value, Class<P> javaType) {
		super.setParameter( name, value, javaType );
		return this;
	}

	@Override
	public <P> SelectionQuery<R> setParameter(String name, P value, BindableType<P> type) {
		super.setParameter( name, value, type );
		return this;
	}

	@Override
	public SelectionQuery<R> setParameter(String name, Instant value, TemporalType temporalType) {
		super.setParameter( name, value, temporalType );
		return this;
	}

	@Override
	public SelectionQuery<R> setParameter(int position, Object value) {
		super.setParameter( position, value );
		return this;
	}

	@Override
	public <P> SelectionQuery<R> setParameter(int position, P value, Class<P> javaType) {
		super.setParameter( position, value, javaType );
		return this;
	}

	@Override
	public <P> SelectionQuery<R> setParameter(int position, P value, BindableType<P> type) {
		super.setParameter( position, value, type );
		return this;
	}

	@Override
	public SelectionQuery<R> setParameter(int position, Instant value, TemporalType temporalType) {
		super.setParameter( position, value, temporalType );
		return this;
	}

	@Override
	public <P> SelectionQuery<R> setParameter(QueryParameter<P> parameter, P value) {
		super.setParameter( parameter, value );
		return this;
	}

	@Override
	public <P> SelectionQuery<R> setParameter(QueryParameter<P> parameter, P value, Class<P> javaType) {
		super.setParameter( parameter, value, javaType );
		return this;
	}

	@Override
	public <P> SelectionQuery<R> setParameter(QueryParameter<P> parameter, P value, BindableType<P> type) {
		super.setParameter( parameter, value, type );
		return this;
	}

	@Override
	public <P> SelectionQuery<R> setParameter(Parameter<P> parameter, P value) {
		super.setParameter( parameter, value );
		return this;
	}

	@Override
	public SelectionQuery<R> setParameter(Parameter<Calendar> param, Calendar value, TemporalType temporalType) {
		super.setParameter( param, value, temporalType );
		return this;
	}

	@Override
	public SelectionQuery<R> setParameter(Parameter<Date> param, Date value, TemporalType temporalType) {
		super.setParameter( param, value, temporalType );
		return this;
	}

	@Override
	public SelectionQuery<R> setParameter(String name, Calendar value, TemporalType temporalType) {
		super.setParameter( name, value, temporalType );
		return this;
	}

	@Override
	public SelectionQuery<R> setParameter(String name, Date value, TemporalType temporalType) {
		super.setParameter( name, value, temporalType );
		return this;
	}

	@Override
	public SelectionQuery<R> setParameter(int position, Calendar value, TemporalType temporalType) {
		super.setParameter( position, value, temporalType );
		return this;
	}

	@Override
	public SelectionQuery<R> setParameter(int position, Date value, TemporalType temporalType) {
		super.setParameter( position, value, temporalType );
		return this;
	}

	@Override
	public SelectionQuery<R> setParameterList(String name, Collection values) {
		super.setParameterList( name, values );
		return this;
	}

	@Override
	public <P> SelectionQuery<R> setParameterList(String name, Collection<? extends P> values, Class<P> javaType) {
		super.setParameterList( name, values, javaType );
		return this;
	}

	@Override
	public <P> SelectionQuery<R> setParameterList(String name, Collection<? extends P> values, BindableType<P> type) {
		super.setParameterList( name, values, type );
		return this;
	}

	@Override
	public SelectionQuery<R> setParameterList(String name, Object[] values) {
		super.setParameterList( name, values );
		return this;
	}

	@Override
	public <P> SelectionQuery<R> setParameterList(String name, P[] values, Class<P> javaType) {
		super.setParameterList( name, values, javaType );
		return this;
	}

	@Override
	public <P> SelectionQuery<R> setParameterList(String name, P[] values, BindableType<P> type) {
		super.setParameterList( name, values, type );
		return this;
	}

	@Override
	public SelectionQuery<R> setParameterList(int position, @SuppressWarnings("rawtypes") Collection values) {
		super.setParameterList( position, values );
		return this;
	}

	@Override
	public <P> SelectionQuery<R> setParameterList(int position, Collection<? extends P> values, Class<P> javaType) {
		super.setParameterList( position, values, javaType );
		return this;
	}

	@Override
	public <P> SelectionQuery<R> setParameterList(int position, Collection<? extends P> values, BindableType<P> type) {
		super.setParameterList( position, values, type );
		return this;
	}

	@Override
	public SelectionQuery<R> setParameterList(int position, Object[] values) {
		super.setParameterList( position, values );
		return this;
	}

	@Override
	public <P> SelectionQuery<R> setParameterList(int position, P[] values, Class<P> javaType) {
		super.setParameterList( position, values, javaType );
		return this;
	}

	@Override
	public <P> SelectionQuery<R> setParameterList(int position, P[] values, BindableType<P> type) {
		super.setParameterList( position, values, type );
		return this;
	}

	@Override
	public <P> SelectionQuery<R> setParameterList(QueryParameter<P> parameter, Collection<? extends P> values) {
		super.setParameterList( parameter, values );
		return this;
	}

	@Override
	public <P> SelectionQuery<R> setParameterList(QueryParameter<P> parameter, Collection<? extends P> values, Class<P> javaType) {
		super.setParameterList( parameter, values, javaType );
		return this;
	}

	@Override
	public <P> SelectionQuery<R> setParameterList(QueryParameter<P> parameter, Collection<? extends P> values, BindableType<P> type) {
		super.setParameterList( parameter, values, type );
		return this;
	}

	@Override
	public <P> SelectionQuery<R> setParameterList(QueryParameter<P> parameter, P[] values) {
		super.setParameterList( parameter, values );
		return this;
	}

	@Override
	public <P> SelectionQuery<R> setParameterList(QueryParameter<P> parameter, P[] values, Class<P> javaType) {
		super.setParameterList( parameter, values, javaType );
		return this;
	}

	@Override
	public <P> SelectionQuery<R> setParameterList(QueryParameter<P> parameter, P[] values, BindableType<P> type) {
		super.setParameterList( parameter, values, type );
		return this;
	}

	@Override
	public SelectionQuery<R> setProperties(Map map) {
		super.setProperties( map );
		return this;
	}

	@Override
	public SelectionQuery<R> setProperties(Object bean) {
		super.setProperties( bean );
		return this;
	}

	public SessionFactoryImplementor getSessionFactory() {
		return getSession().getFactory();
	}
}
