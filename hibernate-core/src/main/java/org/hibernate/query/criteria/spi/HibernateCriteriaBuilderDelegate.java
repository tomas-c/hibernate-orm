/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.query.criteria.spi;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.Incubating;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCoalesce;
import org.hibernate.query.criteria.JpaCollectionJoin;
import org.hibernate.query.criteria.JpaCompoundSelection;
import org.hibernate.query.criteria.JpaCriteriaDelete;
import org.hibernate.query.criteria.JpaCriteriaInsertSelect;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaCriteriaUpdate;
import org.hibernate.query.criteria.JpaCteCriteriaAttribute;
import org.hibernate.query.criteria.JpaExpression;
import org.hibernate.query.criteria.JpaFunction;
import org.hibernate.query.criteria.JpaInPredicate;
import org.hibernate.query.criteria.JpaJoin;
import org.hibernate.query.criteria.JpaListJoin;
import org.hibernate.query.criteria.JpaMapJoin;
import org.hibernate.query.criteria.JpaOrder;
import org.hibernate.query.criteria.JpaParameterExpression;
import org.hibernate.query.criteria.JpaPath;
import org.hibernate.query.criteria.JpaPredicate;
import org.hibernate.query.criteria.JpaRoot;
import org.hibernate.query.criteria.JpaSearchOrder;
import org.hibernate.query.criteria.JpaSearchedCase;
import org.hibernate.query.criteria.JpaSelection;
import org.hibernate.query.criteria.JpaSetJoin;
import org.hibernate.query.criteria.JpaSimpleCase;
import org.hibernate.query.criteria.JpaSubQuery;
import org.hibernate.query.criteria.JpaWindow;
import org.hibernate.query.criteria.JpaWindowFrame;
import org.hibernate.query.sqm.NullPrecedence;
import org.hibernate.query.sqm.SortOrder;
import org.hibernate.query.sqm.TemporalUnit;
import org.hibernate.query.sqm.tree.expression.SqmExpression;

import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CollectionJoin;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.ListJoin;
import jakarta.persistence.criteria.MapJoin;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.criteria.SetJoin;
import jakarta.persistence.criteria.Subquery;

public class HibernateCriteriaBuilderDelegate implements HibernateCriteriaBuilder {
	private final HibernateCriteriaBuilder criteriaBuilder;

	public HibernateCriteriaBuilderDelegate(HibernateCriteriaBuilder criteriaBuilder) {
		this.criteriaBuilder = criteriaBuilder;
	}

	protected HibernateCriteriaBuilder getCriteriaBuilder() {
		return criteriaBuilder;
	}

	@Override
	public <X, T> JpaExpression<X> cast(JpaExpression<T> expression, Class<X> castTargetJavaType) {
		return criteriaBuilder.cast( expression, castTargetJavaType );
	}

	@Override
	public JpaPredicate wrap(Expression<Boolean> expression) {
		return criteriaBuilder.wrap( expression );
	}

	@Override
	public JpaPredicate wrap(Expression<Boolean>... expressions) {
		return criteriaBuilder.wrap( expressions );
	}

	@Override
	public <T extends HibernateCriteriaBuilder> T unwrap(Class<T> clazz) {
		return criteriaBuilder.unwrap( clazz );
	}

	@Override
	public JpaCriteriaQuery<Object> createQuery() {
		return criteriaBuilder.createQuery();
	}

	@Override
	public <T> JpaCriteriaQuery<T> createQuery(Class<T> resultClass) {
		return criteriaBuilder.createQuery( resultClass );
	}

	@Override
	public JpaCriteriaQuery<Tuple> createTupleQuery() {
		return criteriaBuilder.createTupleQuery();
	}

	@Override
	public <T> JpaCriteriaUpdate<T> createCriteriaUpdate(Class<T> targetEntity) {
		return criteriaBuilder.createCriteriaUpdate( targetEntity );
	}

	@Override
	public <T> JpaCriteriaDelete<T> createCriteriaDelete(Class<T> targetEntity) {
		return criteriaBuilder.createCriteriaDelete( targetEntity );
	}

	@Override
	public <T> JpaCriteriaInsertSelect<T> createCriteriaInsertSelect(Class<T> targetEntity) {
		return criteriaBuilder.createCriteriaInsertSelect( targetEntity );
	}

	@Override
	public <T> JpaCriteriaQuery<T> unionAll(CriteriaQuery<? extends T> query1, CriteriaQuery<?>... queries) {
		return criteriaBuilder.unionAll( query1, queries );
	}

	@Override
	public <T> JpaCriteriaQuery<T> union(CriteriaQuery<? extends T> query1, CriteriaQuery<?>... queries) {
		return criteriaBuilder.union( query1, queries );
	}

	@Override
	public <T> JpaCriteriaQuery<T> union(boolean all, CriteriaQuery<? extends T> query1, CriteriaQuery<?>... queries) {
		return criteriaBuilder.union( all, query1, queries );
	}

	@Override
	public <T> JpaCriteriaQuery<T> intersectAll(CriteriaQuery<? extends T> query1, CriteriaQuery<?>... queries) {
		return criteriaBuilder.intersectAll( query1, queries );
	}

	@Override
	public <T> JpaCriteriaQuery<T> intersect(CriteriaQuery<? extends T> query1, CriteriaQuery<?>... queries) {
		return criteriaBuilder.intersect( query1, queries );
	}

	@Override
	public <T> JpaCriteriaQuery<T> intersect(
			boolean all,
			CriteriaQuery<? extends T> query1,
			CriteriaQuery<?>... queries) {
		return criteriaBuilder.intersect( all, query1, queries );
	}

	@Override
	public <T> JpaCriteriaQuery<T> exceptAll(CriteriaQuery<? extends T> query1, CriteriaQuery<?>... queries) {
		return criteriaBuilder.exceptAll( query1, queries );
	}

	@Override
	public <T> JpaCriteriaQuery<T> except(CriteriaQuery<? extends T> query1, CriteriaQuery<?>... queries) {
		return criteriaBuilder.except( query1, queries );
	}

	@Override
	public <T> JpaCriteriaQuery<T> except(boolean all, CriteriaQuery<? extends T> query1, CriteriaQuery<?>... queries) {
		return criteriaBuilder.except( all, query1, queries );
	}

	@Override
	public <T> JpaSubQuery<T> unionAll(Subquery<? extends T> query1, Subquery<?>... queries) {
		return criteriaBuilder.unionAll( query1, queries );
	}

	@Override
	public <T> JpaSubQuery<T> union(Subquery<? extends T> query1, Subquery<?>... queries) {
		return criteriaBuilder.union( query1, queries );
	}

	@Override
	public <T> JpaSubQuery<T> union(boolean all, Subquery<? extends T> query1, Subquery<?>... queries) {
		return criteriaBuilder.union( all, query1, queries );
	}

	@Override
	public <T> JpaSubQuery<T> intersectAll(Subquery<? extends T> query1, Subquery<?>... queries) {
		return criteriaBuilder.intersectAll( query1, queries );
	}

	@Override
	public <T> JpaSubQuery<T> intersect(Subquery<? extends T> query1, Subquery<?>... queries) {
		return criteriaBuilder.intersect( query1, queries );
	}

	@Override
	public <T> JpaSubQuery<T> intersect(boolean all, Subquery<? extends T> query1, Subquery<?>... queries) {
		return criteriaBuilder.intersect( all, query1, queries );
	}

	@Override
	public <T> JpaSubQuery<T> exceptAll(Subquery<? extends T> query1, Subquery<?>... queries) {
		return criteriaBuilder.exceptAll( query1, queries );
	}

	@Override
	public <T> JpaSubQuery<T> except(Subquery<? extends T> query1, Subquery<?>... queries) {
		return criteriaBuilder.except( query1, queries );
	}

	@Override
	public <T> JpaSubQuery<T> except(boolean all, Subquery<? extends T> query1, Subquery<?>... queries) {
		return criteriaBuilder.except( all, query1, queries );
	}

	@Override
	public JpaExpression<Integer> sign(Expression<? extends Number> x) {
		return criteriaBuilder.sign( x );
	}

	@Override
	public <N extends Number> JpaExpression<N> ceiling(Expression<N> x) {
		return criteriaBuilder.ceiling( x );
	}

	@Override
	public <N extends Number> JpaExpression<N> floor(Expression<N> x) {
		return criteriaBuilder.floor( x );
	}

	@Override
	public JpaExpression<Double> exp(Expression<? extends Number> x) {
		return criteriaBuilder.exp( x );
	}

	@Override
	public JpaExpression<Double> ln(Expression<? extends Number> x) {
		return criteriaBuilder.ln( x );
	}

	@Override
	public JpaExpression<Double> power(Expression<? extends Number> x, Expression<? extends Number> y) {
		return criteriaBuilder.power( x, y );
	}

	@Override
	public JpaExpression<Double> power(Expression<? extends Number> x, Number y) {
		return criteriaBuilder.power( x, y );
	}

	@Override
	public <T extends Number> JpaExpression<T> round(Expression<T> x, Integer n) {
		return criteriaBuilder.round( x, n );
	}

	@Override
	public <T extends Number> JpaExpression<T> truncate(Expression<T> x, Integer n) {
		return criteriaBuilder.truncate( x, n );
	}

	@Override
	public JpaExpression<LocalDate> localDate() {
		return criteriaBuilder.localDate();
	}

	@Override
	public JpaExpression<LocalDateTime> localDateTime() {
		return criteriaBuilder.localDateTime();
	}

	@Override
	public JpaExpression<LocalTime> localTime() {
		return criteriaBuilder.localTime();
	}

	@Override
	public <P, F> JpaExpression<F> fk(Path<P> path) {
		return criteriaBuilder.fk( path );
	}

	@Override
	public <X, T extends X> JpaPath<T> treat(Path<X> path, Class<T> type) {
		return criteriaBuilder.treat( path, type );
	}

	@Override
	public <X, T extends X> JpaRoot<T> treat(Root<X> root, Class<T> type) {
		return criteriaBuilder.treat( root, type );
	}

	@Override
	public <X, T, V extends T> JpaJoin<X, V> treat(Join<X, T> join, Class<V> type) {
		return criteriaBuilder.treat( join, type );
	}

	@Override
	public <X, T, E extends T> JpaCollectionJoin<X, E> treat(CollectionJoin<X, T> join, Class<E> type) {
		return criteriaBuilder.treat( join, type );
	}

	@Override
	public <X, T, E extends T> JpaSetJoin<X, E> treat(SetJoin<X, T> join, Class<E> type) {
		return criteriaBuilder.treat( join, type );
	}

	@Override
	public <X, T, E extends T> JpaListJoin<X, E> treat(ListJoin<X, T> join, Class<E> type) {
		return criteriaBuilder.treat( join, type );
	}

	@Override
	public <X, K, T, V extends T> JpaMapJoin<X, K, V> treat(MapJoin<X, K, T> join, Class<V> type) {
		return criteriaBuilder.treat( join, type );
	}

	@Override
	public <Y> JpaCompoundSelection<Y> construct(Class<Y> resultClass, Selection<?>[] selections) {
		return criteriaBuilder.construct( resultClass, selections );
	}

	@Override
	public <Y> JpaCompoundSelection<Y> construct(Class<Y> resultClass, List<? extends JpaSelection<?>> arguments) {
		return criteriaBuilder.construct( resultClass, arguments );
	}

	@Override
	public JpaCompoundSelection<Tuple> tuple(Selection<?>[] selections) {
		return criteriaBuilder.tuple( selections );
	}

	@Override
	public JpaCompoundSelection<Tuple> tuple(List<? extends JpaSelection<?>> selections) {
		return criteriaBuilder.tuple( selections );
	}

	@Override
	public JpaCompoundSelection<Object[]> array(Selection<?>[] selections) {
		return criteriaBuilder.array( selections );
	}

	@Override
	public JpaCompoundSelection<Object[]> array(List<? extends JpaSelection<?>> selections) {
		return criteriaBuilder.array( selections );
	}

	@Override
	public <Y> JpaCompoundSelection<Y> array(Class<Y> resultClass, Selection<?>[] selections) {
		return criteriaBuilder.array( resultClass, selections );
	}

	@Override
	public <Y> JpaCompoundSelection<Y> array(Class<Y> resultClass, List<? extends JpaSelection<?>> selections) {
		return criteriaBuilder.array( resultClass, selections );
	}

	@Override
	public <N extends Number> JpaExpression<Double> avg(Expression<N> argument) {
		return criteriaBuilder.avg( argument );
	}

	@Override
	public <N extends Number> JpaExpression<N> sum(Expression<N> argument) {
		return criteriaBuilder.sum( argument );
	}

	@Override
	public JpaExpression<Long> sumAsLong(Expression<Integer> argument) {
		return criteriaBuilder.sumAsLong( argument );
	}

	@Override
	public JpaExpression<Double> sumAsDouble(Expression<Float> argument) {
		return criteriaBuilder.sumAsDouble( argument );
	}

	@Override
	public <N extends Number> JpaExpression<N> max(Expression<N> argument) {
		return criteriaBuilder.max( argument );
	}

	@Override
	public <N extends Number> JpaExpression<N> min(Expression<N> argument) {
		return criteriaBuilder.min( argument );
	}

	@Override
	public <X extends Comparable<? super X>> JpaExpression<X> greatest(Expression<X> argument) {
		return criteriaBuilder.greatest( argument );
	}

	@Override
	public <X extends Comparable<? super X>> JpaExpression<X> least(Expression<X> argument) {
		return criteriaBuilder.least( argument );
	}

	@Override
	public JpaExpression<Long> count(Expression<?> argument) {
		return criteriaBuilder.count( argument );
	}

	@Override
	public JpaExpression<Long> countDistinct(Expression<?> x) {
		return criteriaBuilder.countDistinct( x );
	}

	@Override
	public <N extends Number> JpaExpression<N> neg(Expression<N> x) {
		return criteriaBuilder.neg( x );
	}

	@Override
	public <N extends Number> JpaExpression<N> abs(Expression<N> x) {
		return criteriaBuilder.abs( x );
	}

	@Override
	public <N extends Number> JpaExpression<N> sum(Expression<? extends N> x, Expression<? extends N> y) {
		return criteriaBuilder.sum( x, y );
	}

	@Override
	public <N extends Number> JpaExpression<N> sum(Expression<? extends N> x, N y) {
		return criteriaBuilder.sum( x, y );
	}

	@Override
	public <N extends Number> JpaExpression<N> sum(N x, Expression<? extends N> y) {
		return criteriaBuilder.sum( x, y );
	}

	@Override
	public <N extends Number> JpaExpression<N> prod(Expression<? extends N> x, Expression<? extends N> y) {
		return criteriaBuilder.prod( x, y );
	}

	@Override
	public <N extends Number> JpaExpression<N> prod(Expression<? extends N> x, N y) {
		return criteriaBuilder.prod( x, y );
	}

	@Override
	public <N extends Number> JpaExpression<N> prod(N x, Expression<? extends N> y) {
		return criteriaBuilder.prod( x, y );
	}

	@Override
	public <N extends Number> JpaExpression<N> diff(Expression<? extends N> x, Expression<? extends N> y) {
		return criteriaBuilder.diff( x, y );
	}

	@Override
	public <N extends Number> JpaExpression<N> diff(Expression<? extends N> x, N y) {
		return criteriaBuilder.diff( x, y );
	}

	@Override
	public <N extends Number> JpaExpression<N> diff(N x, Expression<? extends N> y) {
		return criteriaBuilder.diff( x, y );
	}

	@Override
	public JpaExpression<Number> quot(Expression<? extends Number> x, Expression<? extends Number> y) {
		return criteriaBuilder.quot( x, y );
	}

	@Override
	public JpaExpression<Number> quot(Expression<? extends Number> x, Number y) {
		return criteriaBuilder.quot( x, y );
	}

	@Override
	public JpaExpression<Number> quot(Number x, Expression<? extends Number> y) {
		return criteriaBuilder.quot( x, y );
	}

	@Override
	public JpaExpression<Integer> mod(Expression<Integer> x, Expression<Integer> y) {
		return criteriaBuilder.mod( x, y );
	}

	@Override
	public JpaExpression<Integer> mod(Expression<Integer> x, Integer y) {
		return criteriaBuilder.mod( x, y );
	}

	@Override
	public JpaExpression<Integer> mod(Integer x, Expression<Integer> y) {
		return criteriaBuilder.mod( x, y );
	}

	@Override
	public JpaExpression<Double> sqrt(Expression<? extends Number> x) {
		return criteriaBuilder.sqrt( x );
	}

	@Override
	public JpaExpression<Long> toLong(Expression<? extends Number> number) {
		return criteriaBuilder.toLong( number );
	}

	@Override
	public JpaExpression<Integer> toInteger(Expression<? extends Number> number) {
		return criteriaBuilder.toInteger( number );
	}

	@Override
	public JpaExpression<Float> toFloat(Expression<? extends Number> number) {
		return criteriaBuilder.toFloat( number );
	}

	@Override
	public JpaExpression<Double> toDouble(Expression<? extends Number> number) {
		return criteriaBuilder.toDouble( number );
	}

	@Override
	public JpaExpression<BigDecimal> toBigDecimal(Expression<? extends Number> number) {
		return criteriaBuilder.toBigDecimal( number );
	}

	@Override
	public JpaExpression<BigInteger> toBigInteger(Expression<? extends Number> number) {
		return criteriaBuilder.toBigInteger( number );
	}

	@Override
	public JpaExpression<String> toString(Expression<Character> character) {
		return criteriaBuilder.toString( character );
	}

	@Override
	public <T> JpaExpression<T> literal(T value) {
		return criteriaBuilder.literal( value );
	}

	@Override
	public <T> SqmExpression<T> literal(T value, SqmExpression<? extends T> typeInferenceSource) {
		return criteriaBuilder.literal( value, typeInferenceSource );
	}

	@Override
	public <T> List<? extends JpaExpression<T>> literals(T[] values) {
		return criteriaBuilder.literals( values );
	}

	@Override
	public <T> List<? extends JpaExpression<T>> literals(List<T> values) {
		return criteriaBuilder.literals( values );
	}

	@Override
	public <T> JpaExpression<T> nullLiteral(Class<T> resultClass) {
		return criteriaBuilder.nullLiteral( resultClass );
	}

	@Override
	public <T> JpaParameterExpression<T> parameter(Class<T> paramClass) {
		return criteriaBuilder.parameter( paramClass );
	}

	@Override
	public <T> JpaParameterExpression<T> parameter(Class<T> paramClass, String name) {
		return criteriaBuilder.parameter( paramClass, name );
	}

	@Override
	public JpaExpression<String> concat(Expression<String> x, Expression<String> y) {
		return criteriaBuilder.concat( x, y );
	}

	@Override
	public JpaExpression<String> concat(Expression<String> x, String y) {
		return criteriaBuilder.concat( x, y );
	}

	@Override
	public JpaExpression<String> concat(String x, Expression<String> y) {
		return criteriaBuilder.concat( x, y );
	}

	@Override
	public JpaExpression<String> concat(String x, String y) {
		return criteriaBuilder.concat( x, y );
	}

	@Override
	public JpaFunction<String> substring(Expression<String> x, Expression<Integer> from) {
		return criteriaBuilder.substring( x, from );
	}

	@Override
	public JpaFunction<String> substring(Expression<String> x, int from) {
		return criteriaBuilder.substring( x, from );
	}

	@Override
	public JpaFunction<String> substring(Expression<String> x, Expression<Integer> from, Expression<Integer> len) {
		return criteriaBuilder.substring( x, from, len );
	}

	@Override
	public JpaFunction<String> substring(Expression<String> x, int from, int len) {
		return criteriaBuilder.substring( x, from, len );
	}

	@Override
	public JpaFunction<String> trim(Expression<String> x) {
		return criteriaBuilder.trim( x );
	}

	@Override
	public JpaFunction<String> trim(Trimspec ts, Expression<String> x) {
		return criteriaBuilder.trim( ts, x );
	}

	@Override
	public JpaFunction<String> trim(Expression<Character> t, Expression<String> x) {
		return criteriaBuilder.trim( t, x );
	}

	@Override
	public JpaFunction<String> trim(Trimspec ts, Expression<Character> t, Expression<String> x) {
		return criteriaBuilder.trim( ts, t, x );
	}

	@Override
	public JpaFunction<String> trim(char t, Expression<String> x) {
		return criteriaBuilder.trim( t, x );
	}

	@Override
	public JpaFunction<String> trim(Trimspec ts, char t, Expression<String> x) {
		return criteriaBuilder.trim( ts, t, x );
	}

	@Override
	public JpaFunction<String> lower(Expression<String> x) {
		return criteriaBuilder.lower( x );
	}

	@Override
	public JpaFunction<String> upper(Expression<String> x) {
		return criteriaBuilder.upper( x );
	}

	@Override
	public JpaFunction<Integer> length(Expression<String> x) {
		return criteriaBuilder.length( x );
	}

	@Override
	public JpaFunction<Integer> locate(Expression<String> x, Expression<String> pattern) {
		return criteriaBuilder.locate( x, pattern );
	}

	@Override
	public JpaFunction<Integer> locate(Expression<String> x, String pattern) {
		return criteriaBuilder.locate( x, pattern );
	}

	@Override
	public JpaFunction<Integer> locate(Expression<String> x, Expression<String> pattern, Expression<Integer> from) {
		return criteriaBuilder.locate( x, pattern, from );
	}

	@Override
	public JpaFunction<Integer> locate(Expression<String> x, String pattern, int from) {
		return criteriaBuilder.locate( x, pattern, from );
	}

	@Override
	public JpaFunction<Date> currentDate() {
		return criteriaBuilder.currentDate();
	}

	@Override
	public JpaFunction<Time> currentTime() {
		return criteriaBuilder.currentTime();
	}

	@Override
	public JpaFunction<Timestamp> currentTimestamp() {
		return criteriaBuilder.currentTimestamp();
	}

	@Override
	public JpaFunction<Instant> currentInstant() {
		return criteriaBuilder.currentInstant();
	}

	@Override
	public <T> JpaFunction<T> function(String name, Class<T> type, Expression<?>... args) {
		return criteriaBuilder.function( name, type, args );
	}

	@Override
	public <Y> JpaExpression<Y> all(Subquery<Y> subquery) {
		return criteriaBuilder.all( subquery );
	}

	@Override
	public <Y> JpaExpression<Y> some(Subquery<Y> subquery) {
		return criteriaBuilder.some( subquery );
	}

	@Override
	public <Y> JpaExpression<Y> any(Subquery<Y> subquery) {
		return criteriaBuilder.any( subquery );
	}

	@Override
	public <K, M extends Map<K, ?>> JpaExpression<Set<K>> keys(M map) {
		return criteriaBuilder.keys( map );
	}

	@Override
	public <K, L extends List<?>> JpaExpression<Set<K>> indexes(L list) {
		return criteriaBuilder.indexes( list );
	}

	@Override
	public <T> SqmExpression<T> value(T value) {
		return criteriaBuilder.value( value );
	}

	@Override
	public <T> SqmExpression<T> value(T value, SqmExpression<? extends T> typeInferenceSource) {
		return criteriaBuilder.value( value, typeInferenceSource );
	}

	@Override
	public <V, C extends Collection<V>> JpaExpression<Collection<V>> values(C collection) {
		return criteriaBuilder.values( collection );
	}

	@Override
	public <V, M extends Map<?, V>> Expression<Collection<V>> values(M map) {
		return criteriaBuilder.values( map );
	}

	@Override
	public <C extends Collection<?>> JpaExpression<Integer> size(Expression<C> collection) {
		return criteriaBuilder.size( collection );
	}

	@Override
	public <C extends Collection<?>> JpaExpression<Integer> size(C collection) {
		return criteriaBuilder.size( collection );
	}

	@Override
	public <T> JpaCoalesce<T> coalesce() {
		return criteriaBuilder.coalesce();
	}

	@Override
	public <Y> JpaCoalesce<Y> coalesce(Expression<? extends Y> x, Expression<? extends Y> y) {
		return criteriaBuilder.coalesce( x, y );
	}

	@Override
	public <Y> JpaCoalesce<Y> coalesce(Expression<? extends Y> x, Y y) {
		return criteriaBuilder.coalesce( x, y );
	}

	@Override
	public <Y> JpaExpression<Y> nullif(Expression<Y> x, Expression<?> y) {
		return criteriaBuilder.nullif( x, y );
	}

	@Override
	public <Y> JpaExpression<Y> nullif(Expression<Y> x, Y y) {
		return criteriaBuilder.nullif( x, y );
	}

	@Override
	public <C, R> JpaSimpleCase<C, R> selectCase(Expression<? extends C> expression) {
		return criteriaBuilder.selectCase( expression );
	}

	@Override
	public <R> JpaSearchedCase<R> selectCase() {
		return criteriaBuilder.selectCase();
	}

	@Override
	public JpaPredicate and(Expression<Boolean> x, Expression<Boolean> y) {
		return criteriaBuilder.and( x, y );
	}

	@Override
	public JpaPredicate and(Predicate... restrictions) {
		return criteriaBuilder.and( restrictions );
	}

	@Override
	public JpaPredicate or(Expression<Boolean> x, Expression<Boolean> y) {
		return criteriaBuilder.or( x, y );
	}

	@Override
	public JpaPredicate or(Predicate... restrictions) {
		return criteriaBuilder.or( restrictions );
	}

	@Override
	public JpaPredicate not(Expression<Boolean> restriction) {
		return criteriaBuilder.not( restriction );
	}

	@Override
	public JpaPredicate conjunction() {
		return criteriaBuilder.conjunction();
	}

	@Override
	public JpaPredicate disjunction() {
		return criteriaBuilder.disjunction();
	}

	@Override
	public JpaPredicate isTrue(Expression<Boolean> x) {
		return criteriaBuilder.isTrue( x );
	}

	@Override
	public JpaPredicate isFalse(Expression<Boolean> x) {
		return criteriaBuilder.isFalse( x );
	}

	@Override
	public JpaPredicate isNull(Expression<?> x) {
		return criteriaBuilder.isNull( x );
	}

	@Override
	public JpaPredicate isNotNull(Expression<?> x) {
		return criteriaBuilder.isNotNull( x );
	}

	@Override
	public JpaPredicate equal(Expression<?> x, Expression<?> y) {
		return criteriaBuilder.equal( x, y );
	}

	@Override
	public JpaPredicate equal(Expression<?> x, Object y) {
		return criteriaBuilder.equal( x, y );
	}

	@Override
	public JpaPredicate notEqual(Expression<?> x, Expression<?> y) {
		return criteriaBuilder.notEqual( x, y );
	}

	@Override
	public JpaPredicate notEqual(Expression<?> x, Object y) {
		return criteriaBuilder.notEqual( x, y );
	}

	@Override
	public JpaPredicate distinctFrom(Expression<?> x, Expression<?> y) {
		return criteriaBuilder.distinctFrom( x, y );
	}

	@Override
	public JpaPredicate distinctFrom(Expression<?> x, Object y) {
		return criteriaBuilder.distinctFrom( x, y );
	}

	@Override
	public JpaPredicate notDistinctFrom(Expression<?> x, Expression<?> y) {
		return criteriaBuilder.notDistinctFrom( x, y );
	}

	@Override
	public JpaPredicate notDistinctFrom(Expression<?> x, Object y) {
		return criteriaBuilder.notDistinctFrom( x, y );
	}

	@Override
	public <Y extends Comparable<? super Y>> JpaPredicate greaterThan(
			Expression<? extends Y> x,
			Expression<? extends Y> y) {
		return criteriaBuilder.greaterThan( x, y );
	}

	@Override
	public <Y extends Comparable<? super Y>> JpaPredicate greaterThan(Expression<? extends Y> x, Y y) {
		return criteriaBuilder.greaterThan( x, y );
	}

	@Override
	public <Y extends Comparable<? super Y>> JpaPredicate greaterThanOrEqualTo(
			Expression<? extends Y> x,
			Expression<? extends Y> y) {
		return criteriaBuilder.greaterThanOrEqualTo( x, y );
	}

	@Override
	public <Y extends Comparable<? super Y>> JpaPredicate greaterThanOrEqualTo(Expression<? extends Y> x, Y y) {
		return criteriaBuilder.greaterThanOrEqualTo( x, y );
	}

	@Override
	public <Y extends Comparable<? super Y>> JpaPredicate lessThan(
			Expression<? extends Y> x,
			Expression<? extends Y> y) {
		return criteriaBuilder.lessThan( x, y );
	}

	@Override
	public <Y extends Comparable<? super Y>> JpaPredicate lessThan(Expression<? extends Y> x, Y y) {
		return criteriaBuilder.lessThan( x, y );
	}

	@Override
	public <Y extends Comparable<? super Y>> JpaPredicate lessThanOrEqualTo(
			Expression<? extends Y> x,
			Expression<? extends Y> y) {
		return criteriaBuilder.lessThanOrEqualTo( x, y );
	}

	@Override
	public <Y extends Comparable<? super Y>> JpaPredicate lessThanOrEqualTo(Expression<? extends Y> x, Y y) {
		return criteriaBuilder.lessThanOrEqualTo( x, y );
	}

	@Override
	public <Y extends Comparable<? super Y>> JpaPredicate between(
			Expression<? extends Y> value,
			Expression<? extends Y> lower,
			Expression<? extends Y> upper) {
		return criteriaBuilder.between( value, lower, upper );
	}

	@Override
	public <Y extends Comparable<? super Y>> JpaPredicate between(Expression<? extends Y> value, Y lower, Y upper) {
		return criteriaBuilder.between( value, lower, upper );
	}

	@Override
	public JpaPredicate gt(Expression<? extends Number> x, Expression<? extends Number> y) {
		return criteriaBuilder.gt( x, y );
	}

	@Override
	public JpaPredicate gt(Expression<? extends Number> x, Number y) {
		return criteriaBuilder.gt( x, y );
	}

	@Override
	public JpaPredicate ge(Expression<? extends Number> x, Expression<? extends Number> y) {
		return criteriaBuilder.ge( x, y );
	}

	@Override
	public JpaPredicate ge(Expression<? extends Number> x, Number y) {
		return criteriaBuilder.ge( x, y );
	}

	@Override
	public JpaPredicate lt(Expression<? extends Number> x, Expression<? extends Number> y) {
		return criteriaBuilder.lt( x, y );
	}

	@Override
	public JpaPredicate lt(Expression<? extends Number> x, Number y) {
		return criteriaBuilder.lt( x, y );
	}

	@Override
	public JpaPredicate le(Expression<? extends Number> x, Expression<? extends Number> y) {
		return criteriaBuilder.le( x, y );
	}

	@Override
	public JpaPredicate le(Expression<? extends Number> x, Number y) {
		return criteriaBuilder.le( x, y );
	}

	@Override
	public <C extends Collection<?>> JpaPredicate isEmpty(Expression<C> collection) {
		return criteriaBuilder.isEmpty( collection );
	}

	@Override
	public <C extends Collection<?>> JpaPredicate isNotEmpty(Expression<C> collection) {
		return criteriaBuilder.isNotEmpty( collection );
	}

	@Override
	public <E, C extends Collection<E>> JpaPredicate isMember(Expression<E> elem, Expression<C> collection) {
		return criteriaBuilder.isMember( elem, collection );
	}

	@Override
	public <E, C extends Collection<E>> JpaPredicate isMember(E elem, Expression<C> collection) {
		return criteriaBuilder.isMember( elem, collection );
	}

	@Override
	public <E, C extends Collection<E>> JpaPredicate isNotMember(Expression<E> elem, Expression<C> collection) {
		return criteriaBuilder.isNotMember( elem, collection );
	}

	@Override
	public <E, C extends Collection<E>> JpaPredicate isNotMember(E elem, Expression<C> collection) {
		return criteriaBuilder.isNotMember( elem, collection );
	}

	@Override
	public JpaPredicate like(Expression<String> x, Expression<String> pattern) {
		return criteriaBuilder.like( x, pattern );
	}

	@Override
	public JpaPredicate like(Expression<String> x, String pattern) {
		return criteriaBuilder.like( x, pattern );
	}

	@Override
	public JpaPredicate like(Expression<String> x, Expression<String> pattern, Expression<Character> escapeChar) {
		return criteriaBuilder.like( x, pattern, escapeChar );
	}

	@Override
	public JpaPredicate like(Expression<String> x, Expression<String> pattern, char escapeChar) {
		return criteriaBuilder.like( x, pattern, escapeChar );
	}

	@Override
	public JpaPredicate like(Expression<String> x, String pattern, Expression<Character> escapeChar) {
		return criteriaBuilder.like( x, pattern, escapeChar );
	}

	@Override
	public JpaPredicate like(Expression<String> x, String pattern, char escapeChar) {
		return criteriaBuilder.like( x, pattern, escapeChar );
	}

	@Override
	public JpaPredicate ilike(Expression<String> x, Expression<String> pattern) {
		return criteriaBuilder.ilike( x, pattern );
	}

	@Override
	public JpaPredicate ilike(Expression<String> x, String pattern) {
		return criteriaBuilder.ilike( x, pattern );
	}

	@Override
	public JpaPredicate ilike(Expression<String> x, Expression<String> pattern, Expression<Character> escapeChar) {
		return criteriaBuilder.ilike( x, pattern, escapeChar );
	}

	@Override
	public JpaPredicate ilike(Expression<String> x, Expression<String> pattern, char escapeChar) {
		return criteriaBuilder.ilike( x, pattern, escapeChar );
	}

	@Override
	public JpaPredicate ilike(Expression<String> x, String pattern, Expression<Character> escapeChar) {
		return criteriaBuilder.ilike( x, pattern, escapeChar );
	}

	@Override
	public JpaPredicate ilike(Expression<String> x, String pattern, char escapeChar) {
		return criteriaBuilder.ilike( x, pattern, escapeChar );
	}

	@Override
	public JpaPredicate notLike(Expression<String> x, Expression<String> pattern) {
		return criteriaBuilder.notLike( x, pattern );
	}

	@Override
	public JpaPredicate notLike(Expression<String> x, String pattern) {
		return criteriaBuilder.notLike( x, pattern );
	}

	@Override
	public JpaPredicate notLike(Expression<String> x, Expression<String> pattern, Expression<Character> escapeChar) {
		return criteriaBuilder.notLike( x, pattern, escapeChar );
	}

	@Override
	public JpaPredicate notLike(Expression<String> x, Expression<String> pattern, char escapeChar) {
		return criteriaBuilder.notLike( x, pattern, escapeChar );
	}

	@Override
	public JpaPredicate notLike(Expression<String> x, String pattern, Expression<Character> escapeChar) {
		return criteriaBuilder.notLike( x, pattern, escapeChar );
	}

	@Override
	public JpaPredicate notLike(Expression<String> x, String pattern, char escapeChar) {
		return criteriaBuilder.notLike( x, pattern, escapeChar );
	}

	@Override
	public JpaPredicate notIlike(Expression<String> x, Expression<String> pattern) {
		return criteriaBuilder.notIlike( x, pattern );
	}

	@Override
	public JpaPredicate notIlike(Expression<String> x, String pattern) {
		return criteriaBuilder.notIlike( x, pattern );
	}

	@Override
	public JpaPredicate notIlike(Expression<String> x, Expression<String> pattern, Expression<Character> escapeChar) {
		return criteriaBuilder.notIlike( x, pattern, escapeChar );
	}

	@Override
	public JpaPredicate notIlike(Expression<String> x, Expression<String> pattern, char escapeChar) {
		return criteriaBuilder.notIlike( x, pattern, escapeChar );
	}

	@Override
	public JpaPredicate notIlike(Expression<String> x, String pattern, Expression<Character> escapeChar) {
		return criteriaBuilder.notIlike( x, pattern, escapeChar );
	}

	@Override
	public JpaPredicate notIlike(Expression<String> x, String pattern, char escapeChar) {
		return criteriaBuilder.notIlike( x, pattern, escapeChar );
	}

	@Override
	public <T> JpaInPredicate<T> in(Expression<? extends T> expression) {
		return criteriaBuilder.in( expression );
	}

	@Override
	public <T> JpaInPredicate<T> in(Expression<? extends T> expression, Expression<? extends T>... values) {
		return criteriaBuilder.in( expression, values );
	}

	@Override
	public <T> JpaInPredicate<T> in(Expression<? extends T> expression, T... values) {
		return criteriaBuilder.in( expression, values );
	}

	@Override
	public <T> JpaInPredicate<T> in(Expression<? extends T> expression, Collection<T> values) {
		return criteriaBuilder.in( expression, values );
	}

	@Override
	public JpaPredicate exists(Subquery<?> subquery) {
		return criteriaBuilder.exists( subquery );
	}

	@Override
	public <M extends Map<?, ?>> JpaPredicate isMapEmpty(JpaExpression<M> mapExpression) {
		return criteriaBuilder.isMapEmpty( mapExpression );
	}

	@Override
	public <M extends Map<?, ?>> JpaPredicate isMapNotEmpty(JpaExpression<M> mapExpression) {
		return criteriaBuilder.isMapNotEmpty( mapExpression );
	}

	@Override
	public <M extends Map<?, ?>> JpaExpression<Integer> mapSize(JpaExpression<M> mapExpression) {
		return criteriaBuilder.mapSize( mapExpression );
	}

	@Override
	public <M extends Map<?, ?>> JpaExpression<Integer> mapSize(M map) {
		return criteriaBuilder.mapSize( map );
	}

	@Override
	public JpaOrder sort(JpaExpression<?> sortExpression, SortOrder sortOrder, NullPrecedence nullPrecedence) {
		return criteriaBuilder.sort( sortExpression, sortOrder, nullPrecedence );
	}

	@Override
	public JpaOrder sort(JpaExpression<?> sortExpression, SortOrder sortOrder) {
		return criteriaBuilder.sort( sortExpression, sortOrder );
	}

	@Override
	public JpaOrder sort(JpaExpression<?> sortExpression) {
		return criteriaBuilder.sort( sortExpression );
	}

	@Override
	public JpaOrder asc(Expression<?> x) {
		return criteriaBuilder.asc( x );
	}

	@Override
	public JpaOrder desc(Expression<?> x) {
		return criteriaBuilder.desc( x );
	}

	@Override
	public JpaOrder asc(Expression<?> x, boolean nullsFirst) {
		return criteriaBuilder.asc( x, nullsFirst );
	}

	@Override
	public JpaOrder desc(Expression<?> x, boolean nullsFirst) {
		return criteriaBuilder.desc( x, nullsFirst );
	}

	@Override
	@Incubating
	public JpaSearchOrder search(
			JpaCteCriteriaAttribute cteAttribute,
			SortOrder sortOrder,
			NullPrecedence nullPrecedence) {
		return criteriaBuilder.search( cteAttribute, sortOrder, nullPrecedence );
	}

	@Override
	@Incubating
	public JpaSearchOrder search(JpaCteCriteriaAttribute cteAttribute, SortOrder sortOrder) {
		return criteriaBuilder.search( cteAttribute, sortOrder );
	}

	@Override
	@Incubating
	public JpaSearchOrder search(JpaCteCriteriaAttribute cteAttribute) {
		return criteriaBuilder.search( cteAttribute );
	}

	@Override
	@Incubating
	public JpaSearchOrder asc(JpaCteCriteriaAttribute x) {
		return criteriaBuilder.asc( x );
	}

	@Override
	@Incubating
	public JpaSearchOrder desc(JpaCteCriteriaAttribute x) {
		return criteriaBuilder.desc( x );
	}

	@Override
	@Incubating
	public JpaSearchOrder asc(JpaCteCriteriaAttribute x, boolean nullsFirst) {
		return criteriaBuilder.asc( x, nullsFirst );
	}

	@Override
	@Incubating
	public JpaSearchOrder desc(JpaCteCriteriaAttribute x, boolean nullsFirst) {
		return criteriaBuilder.desc( x, nullsFirst );
	}

	@Override
	public <T> JpaExpression<T> sql(String pattern, Class<T> type, Expression<?>... arguments) {
		return criteriaBuilder.sql( pattern, type, arguments );
	}

	@Override
	public JpaFunction<String> format(Expression<? extends TemporalAccessor> datetime, String pattern) {
		return criteriaBuilder.format( datetime, pattern );
	}

	@Override
	public JpaFunction<Integer> year(Expression<? extends TemporalAccessor> datetime) {
		return criteriaBuilder.year( datetime );
	}

	@Override
	public JpaFunction<Integer> month(Expression<? extends TemporalAccessor> datetime) {
		return criteriaBuilder.month( datetime );
	}

	@Override
	public JpaFunction<Integer> day(Expression<? extends TemporalAccessor> datetime) {
		return criteriaBuilder.day( datetime );
	}

	@Override
	public JpaFunction<Integer> hour(Expression<? extends TemporalAccessor> datetime) {
		return criteriaBuilder.hour( datetime );
	}

	@Override
	public JpaFunction<Integer> minute(Expression<? extends TemporalAccessor> datetime) {
		return criteriaBuilder.minute( datetime );
	}

	@Override
	public JpaFunction<Float> second(Expression<? extends TemporalAccessor> datetime) {
		return criteriaBuilder.second( datetime );
	}

	@Override
	public <T extends TemporalAccessor> JpaFunction<T> truncate(Expression<T> datetime, TemporalUnit temporalUnit) {
		return criteriaBuilder.truncate( datetime, temporalUnit );
	}

	@Override
	public JpaFunction<String> overlay(Expression<String> string, String replacement, int start) {
		return criteriaBuilder.overlay( string, replacement, start );
	}

	@Override
	public JpaFunction<String> overlay(Expression<String> string, Expression<String> replacement, int start) {
		return criteriaBuilder.overlay( string, replacement, start );
	}

	@Override
	public JpaFunction<String> overlay(Expression<String> string, String replacement, Expression<Integer> start) {
		return criteriaBuilder.overlay( string, replacement, start );
	}

	@Override
	public JpaFunction<String> overlay(
			Expression<String> string,
			Expression<String> replacement,
			Expression<Integer> start) {
		return criteriaBuilder.overlay( string, replacement, start );
	}

	@Override
	public JpaFunction<String> overlay(Expression<String> string, String replacement, int start, int length) {
		return criteriaBuilder.overlay( string, replacement, start, length );
	}

	@Override
	public JpaFunction<String> overlay(
			Expression<String> string,
			Expression<String> replacement,
			int start,
			int length) {
		return criteriaBuilder.overlay( string, replacement, start, length );
	}

	@Override
	public JpaFunction<String> overlay(
			Expression<String> string,
			String replacement,
			Expression<Integer> start,
			int length) {
		return criteriaBuilder.overlay( string, replacement, start, length );
	}

	@Override
	public JpaFunction<String> overlay(
			Expression<String> string,
			Expression<String> replacement,
			Expression<Integer> start,
			int length) {
		return criteriaBuilder.overlay( string, replacement, start, length );
	}

	@Override
	public JpaFunction<String> overlay(
			Expression<String> string,
			String replacement,
			int start,
			Expression<Integer> length) {
		return criteriaBuilder.overlay( string, replacement, start, length );
	}

	@Override
	public JpaFunction<String> overlay(
			Expression<String> string,
			Expression<String> replacement,
			int start,
			Expression<Integer> length) {
		return criteriaBuilder.overlay( string, replacement, start, length );
	}

	@Override
	public JpaFunction<String> overlay(
			Expression<String> string,
			String replacement,
			Expression<Integer> start,
			Expression<Integer> length) {
		return criteriaBuilder.overlay( string, replacement, start, length );
	}

	@Override
	public JpaFunction<String> overlay(
			Expression<String> string,
			Expression<String> replacement,
			Expression<Integer> start,
			Expression<Integer> length) {
		return criteriaBuilder.overlay( string, replacement, start, length );
	}

	@Override
	public JpaFunction<String> pad(Expression<String> x, int length) {
		return criteriaBuilder.pad( x, length );
	}

	@Override
	public JpaFunction<String> pad(Trimspec ts, Expression<String> x, int length) {
		return criteriaBuilder.pad( ts, x, length );
	}

	@Override
	public JpaFunction<String> pad(Expression<String> x, Expression<Integer> length) {
		return criteriaBuilder.pad( x, length );
	}

	@Override
	public JpaFunction<String> pad(Trimspec ts, Expression<String> x, Expression<Integer> length) {
		return criteriaBuilder.pad( ts, x, length );
	}

	@Override
	public JpaFunction<String> pad(Expression<String> x, int length, char padChar) {
		return criteriaBuilder.pad( x, length, padChar );
	}

	@Override
	public JpaFunction<String> pad(Trimspec ts, Expression<String> x, int length, char padChar) {
		return criteriaBuilder.pad( ts, x, length, padChar );
	}

	@Override
	public JpaFunction<String> pad(Expression<String> x, Expression<Integer> length, char padChar) {
		return criteriaBuilder.pad( x, length, padChar );
	}

	@Override
	public JpaFunction<String> pad(Trimspec ts, Expression<String> x, Expression<Integer> length, char padChar) {
		return criteriaBuilder.pad( ts, x, length, padChar );
	}

	@Override
	public JpaFunction<String> pad(Expression<String> x, int length, Expression<Character> padChar) {
		return criteriaBuilder.pad( x, length, padChar );
	}

	@Override
	public JpaFunction<String> pad(Trimspec ts, Expression<String> x, int length, Expression<Character> padChar) {
		return criteriaBuilder.pad( ts, x, length, padChar );
	}

	@Override
	public JpaFunction<String> pad(Expression<String> x, Expression<Integer> length, Expression<Character> padChar) {
		return criteriaBuilder.pad( x, length, padChar );
	}

	@Override
	public JpaFunction<String> pad(
			Trimspec ts,
			Expression<String> x,
			Expression<Integer> length,
			Expression<Character> padChar) {
		return criteriaBuilder.pad( ts, x, length, padChar );
	}

	@Override
	public JpaFunction<String> left(Expression<String> x, int length) {
		return criteriaBuilder.left( x, length );
	}

	@Override
	public JpaFunction<String> left(Expression<String> x, Expression<Integer> length) {
		return criteriaBuilder.left( x, length );
	}

	@Override
	public JpaFunction<String> right(Expression<String> x, int length) {
		return criteriaBuilder.right( x, length );
	}

	@Override
	public JpaFunction<String> right(Expression<String> x, Expression<Integer> length) {
		return criteriaBuilder.right( x, length );
	}

	@Override
	public JpaFunction<String> replace(Expression<String> x, String pattern, String replacement) {
		return criteriaBuilder.replace( x, pattern, replacement );
	}

	@Override
	public JpaFunction<String> replace(Expression<String> x, String pattern, Expression<String> replacement) {
		return criteriaBuilder.replace( x, pattern, replacement );
	}

	@Override
	public JpaFunction<String> replace(Expression<String> x, Expression<String> pattern, String replacement) {
		return criteriaBuilder.replace( x, pattern, replacement );
	}

	@Override
	public JpaFunction<String> replace(
			Expression<String> x,
			Expression<String> pattern,
			Expression<String> replacement) {
		return criteriaBuilder.replace( x, pattern, replacement );
	}

	@Override
	public JpaFunction<String> collate(Expression<String> x, String collation) {
		return criteriaBuilder.collate( x, collation );
	}

	@Override
	public JpaExpression<Double> log10(Expression<? extends Number> x) {
		return criteriaBuilder.log10( x );
	}

	@Override
	public JpaExpression<Double> log(Number b, Expression<? extends Number> x) {
		return criteriaBuilder.log( b, x );
	}

	@Override
	public JpaExpression<Double> log(Expression<? extends Number> b, Expression<? extends Number> x) {
		return criteriaBuilder.log( b, x );
	}

	@Override
	public JpaExpression<Double> pi() {
		return criteriaBuilder.pi();
	}

	@Override
	public JpaExpression<Double> sin(Expression<? extends Number> x) {
		return criteriaBuilder.sin( x );
	}

	@Override
	public JpaExpression<Double> cos(Expression<? extends Number> x) {
		return criteriaBuilder.cos( x );
	}

	@Override
	public JpaExpression<Double> tan(Expression<? extends Number> x) {
		return criteriaBuilder.tan( x );
	}

	@Override
	public JpaExpression<Double> asin(Expression<? extends Number> x) {
		return criteriaBuilder.asin( x );
	}

	@Override
	public JpaExpression<Double> acos(Expression<? extends Number> x) {
		return criteriaBuilder.acos( x );
	}

	@Override
	public JpaExpression<Double> atan(Expression<? extends Number> x) {
		return criteriaBuilder.atan( x );
	}

	@Override
	public JpaExpression<Double> atan2(Number y, Expression<? extends Number> x) {
		return criteriaBuilder.atan2( y, x );
	}

	@Override
	public JpaExpression<Double> atan2(Expression<? extends Number> y, Number x) {
		return criteriaBuilder.atan2( y, x );
	}

	@Override
	public JpaExpression<Double> atan2(Expression<? extends Number> y, Expression<? extends Number> x) {
		return criteriaBuilder.atan2( y, x );
	}

	@Override
	public JpaExpression<Double> sinh(Expression<? extends Number> x) {
		return criteriaBuilder.sinh( x );
	}

	@Override
	public JpaExpression<Double> cosh(Expression<? extends Number> x) {
		return criteriaBuilder.cosh( x );
	}

	@Override
	public JpaExpression<Double> tanh(Expression<? extends Number> x) {
		return criteriaBuilder.tanh( x );
	}

	@Override
	public JpaExpression<Double> degrees(Expression<? extends Number> x) {
		return criteriaBuilder.degrees( x );
	}

	@Override
	public JpaExpression<Double> radians(Expression<? extends Number> x) {
		return criteriaBuilder.radians( x );
	}

	@Override
	public JpaWindow createWindow() {
		return criteriaBuilder.createWindow();
	}

	@Override
	public JpaWindowFrame frameUnboundedPreceding() {
		return criteriaBuilder.frameUnboundedPreceding();
	}

	@Override
	public JpaWindowFrame frameBetweenPreceding(int offset) {
		return criteriaBuilder.frameBetweenPreceding( offset );
	}

	@Override
	public JpaWindowFrame frameBetweenPreceding(Expression<?> offset) {
		return criteriaBuilder.frameBetweenPreceding( offset );
	}

	@Override
	public JpaWindowFrame frameCurrentRow() {
		return criteriaBuilder.frameCurrentRow();
	}

	@Override
	public JpaWindowFrame frameBetweenFollowing(int offset) {
		return criteriaBuilder.frameBetweenFollowing( offset );
	}

	@Override
	public JpaWindowFrame frameBetweenFollowing(Expression<?> offset) {
		return criteriaBuilder.frameBetweenFollowing( offset );
	}

	@Override
	public JpaWindowFrame frameUnboundedFollowing() {
		return criteriaBuilder.frameUnboundedFollowing();
	}

	@Override
	public <T> JpaExpression<T> windowFunction(String name, Class<T> type, JpaWindow window, Expression<?>... args) {
		return criteriaBuilder.windowFunction( name, type, window, args );
	}

	@Override
	public JpaExpression<Long> rowNumber(JpaWindow window) {
		return criteriaBuilder.rowNumber( window );
	}

	@Override
	public <T> JpaExpression<T> firstValue(Expression<T> argument, JpaWindow window) {
		return criteriaBuilder.firstValue( argument, window );
	}

	@Override
	public <T> JpaExpression<T> lastValue(Expression<T> argument, JpaWindow window) {
		return criteriaBuilder.lastValue( argument, window );
	}

	@Override
	public <T> JpaExpression<T> nthValue(Expression<T> argument, int n, JpaWindow window) {
		return criteriaBuilder.nthValue( argument, n, window );
	}

	@Override
	public <T> JpaExpression<T> nthValue(Expression<T> argument, Expression<Integer> n, JpaWindow window) {
		return criteriaBuilder.nthValue( argument, n, window );
	}

	@Override
	public JpaExpression<Long> rank(JpaWindow window) {
		return criteriaBuilder.rank( window );
	}

	@Override
	public JpaExpression<Long> denseRank(JpaWindow window) {
		return criteriaBuilder.denseRank( window );
	}

	@Override
	public JpaExpression<Double> percentRank(JpaWindow window) {
		return criteriaBuilder.percentRank( window );
	}

	@Override
	public JpaExpression<Double> cumeDist(JpaWindow window) {
		return criteriaBuilder.cumeDist( window );
	}

	@Override
	public <T> JpaExpression<T> functionAggregate(
			String name,
			Class<T> type,
			JpaPredicate filter,
			Expression<?>... args) {
		return criteriaBuilder.functionAggregate( name, type, filter, args );
	}

	@Override
	public <T> JpaExpression<T> functionAggregate(String name, Class<T> type, JpaWindow window, Expression<?>... args) {
		return criteriaBuilder.functionAggregate( name, type, window, args );
	}

	@Override
	public <T> JpaExpression<T> functionAggregate(
			String name,
			Class<T> type,
			JpaPredicate filter,
			JpaWindow window,
			Expression<?>... args) {
		return criteriaBuilder.functionAggregate( name, type, filter, window, args );
	}

	@Override
	public <N extends Number> JpaExpression<Number> sum(Expression<N> argument, JpaPredicate filter) {
		return criteriaBuilder.sum( argument, filter );
	}

	@Override
	public <N extends Number> JpaExpression<Number> sum(Expression<N> argument, JpaWindow window) {
		return criteriaBuilder.sum( argument, window );
	}

	@Override
	public <N extends Number> JpaExpression<Number> sum(Expression<N> argument, JpaPredicate filter, JpaWindow window) {
		return criteriaBuilder.sum( argument, filter, window );
	}

	@Override
	public <N extends Number> JpaExpression<Double> avg(Expression<N> argument, JpaPredicate filter) {
		return criteriaBuilder.avg( argument, filter );
	}

	@Override
	public <N extends Number> JpaExpression<Double> avg(Expression<N> argument, JpaWindow window) {
		return criteriaBuilder.avg( argument, window );
	}

	@Override
	public <N extends Number> JpaExpression<Double> avg(Expression<N> argument, JpaPredicate filter, JpaWindow window) {
		return criteriaBuilder.avg( argument, filter, window );
	}

	@Override
	public JpaExpression<Long> count(Expression<?> argument, JpaPredicate filter) {
		return criteriaBuilder.count( argument, filter );
	}

	@Override
	public JpaExpression<Long> count(Expression<?> argument, JpaWindow window) {
		return criteriaBuilder.count( argument, window );
	}

	@Override
	public JpaExpression<Long> count(Expression<?> argument, JpaPredicate filter, JpaWindow window) {
		return criteriaBuilder.count( argument, filter, window );
	}

	@Override
	public <T> JpaExpression<T> functionWithinGroup(String name, Class<T> type, JpaOrder order, Expression<?>... args) {
		return criteriaBuilder.functionWithinGroup( name, type, order, args );
	}

	@Override
	public <T> JpaExpression<T> functionWithinGroup(
			String name,
			Class<T> type,
			JpaOrder order,
			JpaPredicate filter,
			Expression<?>... args) {
		return criteriaBuilder.functionWithinGroup( name, type, order, filter, args );
	}

	@Override
	public <T> JpaExpression<T> functionWithinGroup(
			String name,
			Class<T> type,
			JpaOrder order,
			JpaWindow window,
			Expression<?>... args) {
		return criteriaBuilder.functionWithinGroup( name, type, order, window, args );
	}

	@Override
	public <T> JpaExpression<T> functionWithinGroup(
			String name,
			Class<T> type,
			JpaOrder order,
			JpaPredicate filter,
			JpaWindow window,
			Expression<?>... args) {
		return criteriaBuilder.functionWithinGroup( name, type, order, filter, window, args );
	}

	@Override
	public JpaExpression<String> listagg(JpaOrder order, Expression<String> argument, String separator) {
		return criteriaBuilder.listagg( order, argument, separator );
	}

	@Override
	public JpaExpression<String> listagg(JpaOrder order, Expression<String> argument, Expression<String> separator) {
		return criteriaBuilder.listagg( order, argument, separator );
	}

	@Override
	public JpaExpression<String> listagg(
			JpaOrder order,
			JpaPredicate filter,
			Expression<String> argument,
			String separator) {
		return criteriaBuilder.listagg( order, filter, argument, separator );
	}

	@Override
	public JpaExpression<String> listagg(
			JpaOrder order,
			JpaPredicate filter,
			Expression<String> argument,
			Expression<String> separator) {
		return criteriaBuilder.listagg( order, filter, argument, separator );
	}

	@Override
	public JpaExpression<String> listagg(
			JpaOrder order,
			JpaWindow window,
			Expression<String> argument,
			String separator) {
		return criteriaBuilder.listagg( order, window, argument, separator );
	}

	@Override
	public JpaExpression<String> listagg(
			JpaOrder order,
			JpaWindow window,
			Expression<String> argument,
			Expression<String> separator) {
		return criteriaBuilder.listagg( order, window, argument, separator );
	}

	@Override
	public JpaExpression<String> listagg(
			JpaOrder order,
			JpaPredicate filter,
			JpaWindow window,
			Expression<String> argument,
			String separator) {
		return criteriaBuilder.listagg( order, filter, window, argument, separator );
	}

	@Override
	public JpaExpression<String> listagg(
			JpaOrder order,
			JpaPredicate filter,
			JpaWindow window,
			Expression<String> argument,
			Expression<String> separator) {
		return criteriaBuilder.listagg( order, filter, window, argument, separator );
	}

	@Override
	public <T> JpaExpression<T> mode(Expression<T> sortExpression, SortOrder sortOrder, NullPrecedence nullPrecedence) {
		return criteriaBuilder.mode( sortExpression, sortOrder, nullPrecedence );
	}

	@Override
	public <T> JpaExpression<T> mode(
			JpaPredicate filter,
			Expression<T> sortExpression,
			SortOrder sortOrder,
			NullPrecedence nullPrecedence) {
		return criteriaBuilder.mode( filter, sortExpression, sortOrder, nullPrecedence );
	}

	@Override
	public <T> JpaExpression<T> mode(
			JpaWindow window,
			Expression<T> sortExpression,
			SortOrder sortOrder,
			NullPrecedence nullPrecedence) {
		return criteriaBuilder.mode( window, sortExpression, sortOrder, nullPrecedence );
	}

	@Override
	public <T> JpaExpression<T> mode(
			JpaPredicate filter,
			JpaWindow window,
			Expression<T> sortExpression,
			SortOrder sortOrder,
			NullPrecedence nullPrecedence) {
		return criteriaBuilder.mode( filter, window, sortExpression, sortOrder, nullPrecedence );
	}

	@Override
	public <T> JpaExpression<T> percentileCont(
			Expression<? extends Number> argument,
			Expression<T> sortExpression,
			SortOrder sortOrder,
			NullPrecedence nullPrecedence) {
		return criteriaBuilder.percentileCont( argument, sortExpression, sortOrder, nullPrecedence );
	}

	@Override
	public <T> JpaExpression<T> percentileCont(
			Expression<? extends Number> argument,
			JpaPredicate filter,
			Expression<T> sortExpression,
			SortOrder sortOrder,
			NullPrecedence nullPrecedence) {
		return criteriaBuilder.percentileCont( argument, filter, sortExpression, sortOrder, nullPrecedence );
	}

	@Override
	public <T> JpaExpression<T> percentileCont(
			Expression<? extends Number> argument,
			JpaWindow window,
			Expression<T> sortExpression,
			SortOrder sortOrder,
			NullPrecedence nullPrecedence) {
		return criteriaBuilder.percentileCont( argument, window, sortExpression, sortOrder, nullPrecedence );
	}

	@Override
	public <T> JpaExpression<T> percentileCont(
			Expression<? extends Number> argument,
			JpaPredicate filter,
			JpaWindow window,
			Expression<T> sortExpression,
			SortOrder sortOrder,
			NullPrecedence nullPrecedence) {
		return criteriaBuilder.percentileCont( argument, filter, window, sortExpression, sortOrder, nullPrecedence );
	}

	@Override
	public <T> JpaExpression<T> percentileDisc(
			Expression<? extends Number> argument,
			Expression<T> sortExpression,
			SortOrder sortOrder,
			NullPrecedence nullPrecedence) {
		return criteriaBuilder.percentileDisc( argument, sortExpression, sortOrder, nullPrecedence );
	}

	@Override
	public <T> JpaExpression<T> percentileDisc(
			Expression<? extends Number> argument,
			JpaPredicate filter,
			Expression<T> sortExpression,
			SortOrder sortOrder,
			NullPrecedence nullPrecedence) {
		return criteriaBuilder.percentileDisc( argument, filter, sortExpression, sortOrder, nullPrecedence );
	}

	@Override
	public <T> JpaExpression<T> percentileDisc(
			Expression<? extends Number> argument,
			JpaWindow window,
			Expression<T> sortExpression,
			SortOrder sortOrder,
			NullPrecedence nullPrecedence) {
		return criteriaBuilder.percentileDisc( argument, window, sortExpression, sortOrder, nullPrecedence );
	}

	@Override
	public <T> JpaExpression<T> percentileDisc(
			Expression<? extends Number> argument,
			JpaPredicate filter,
			JpaWindow window,
			Expression<T> sortExpression,
			SortOrder sortOrder,
			NullPrecedence nullPrecedence) {
		return criteriaBuilder.percentileDisc( argument, filter, window, sortExpression, sortOrder, nullPrecedence );
	}

	@Override
	public JpaExpression<Long> rank(JpaOrder order, Expression<?>... arguments) {
		return criteriaBuilder.rank( order, arguments );
	}

	@Override
	public JpaExpression<Long> rank(JpaOrder order, JpaPredicate filter, Expression<?>... arguments) {
		return criteriaBuilder.rank( order, filter, arguments );
	}

	@Override
	public JpaExpression<Long> rank(JpaOrder order, JpaWindow window, Expression<?>... arguments) {
		return criteriaBuilder.rank( order, window, arguments );
	}

	@Override
	public JpaExpression<Long> rank(JpaOrder order, JpaPredicate filter, JpaWindow window, Expression<?>... arguments) {
		return criteriaBuilder.rank( order, filter, window, arguments );
	}

	@Override
	public JpaExpression<Double> percentRank(JpaOrder order, Expression<?>... arguments) {
		return criteriaBuilder.percentRank( order, arguments );
	}

	@Override
	public JpaExpression<Double> percentRank(JpaOrder order, JpaPredicate filter, Expression<?>... arguments) {
		return criteriaBuilder.percentRank( order, filter, arguments );
	}

	@Override
	public JpaExpression<Double> percentRank(JpaOrder order, JpaWindow window, Expression<?>... arguments) {
		return criteriaBuilder.percentRank( order, window, arguments );
	}

	@Override
	public JpaExpression<Double> percentRank(
			JpaOrder order,
			JpaPredicate filter,
			JpaWindow window,
			Expression<?>... arguments) {
		return criteriaBuilder.percentRank( order, filter, window, arguments );
	}
}
