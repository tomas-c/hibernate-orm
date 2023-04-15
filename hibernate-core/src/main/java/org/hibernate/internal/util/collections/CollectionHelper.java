/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.internal.util.collections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Various helper util methods for handling collections.
 *
 * @author Gavin King
 * @author Steve Ebersole
 */
public final class CollectionHelper {
	public static final int DEFAULT_LIST_CAPACITY = 10;
	public static final int MINIMUM_INITIAL_CAPACITY = 16;
	public static final float LOAD_FACTOR = 0.75f;

	private CollectionHelper() {
	}

	/**
	 * Build a properly sized map, especially handling load size and load factor to prevent immediate resizing.
	 * <p>
	 * Especially helpful for copy map contents.
	 *
	 * @param size The size to make the map.
	 *
	 * @return The sized map.
	 */
	public static <K, V> HashMap<K, V> mapOfSize(int size) {
		return new HashMap<>( determineProperSizing( size ), LOAD_FACTOR );
	}

	/**
	 * Build a properly sized linked map, especially handling load size and load factor to prevent immediate resizing.
	 * <p>
	 * Especially helpful for copy map contents.
	 *
	 * @param size The size to make the map.
	 *
	 * @return The sized linked map.
	 */
	public static <K, V> LinkedHashMap<K, V> linkedMapOfSize(int size) {
		return new LinkedHashMap<>( determineProperSizing( size ), LOAD_FACTOR );
	}

	/**
	 * Build a map whose size is unknown.
	 *
	 * @return The map.
	 */
	public static <K, V> HashMap<K, V> map() {
		return new HashMap<>();
	}

	/**
	 * Build a linked map whose size is unknown.
	 *
	 * @return The linked map.
	 */
	public static <K, V> LinkedHashMap<K, V> linkedMap() {
		return new LinkedHashMap<>();
	}

	/**
	 * Build a properly sized set, especially handling load size and load factor to prevent immediate resizing.
	 * <p>
	 * Especially helpful for copy set contents.
	 *
	 * @param size The size to make the set.
	 *
	 * @return The sized set.
	 */
	public static <K> HashSet<K> setOfSize(int size) {
		return new HashSet<>( determineProperSizing( size ), LOAD_FACTOR );
	}

	/**
	 * Build a set whose size is unknown.
	 *
	 * @return The set.
	 */
	public static <K> HashSet<K> set() {
		return new HashSet<>();
	}

	/**
	 * Build a properly sized linked set, especially handling load size and load factor to prevent immediate resizing.
	 * <p>
	 * Especially helpful for copy set contents.
	 *
	 * @param size The size to make the set.
	 *
	 * @return The sized linked set.
	 */
	public static <K> LinkedHashSet<K> linkedSetOfSize(int size) {
		return new LinkedHashSet<>( determineProperSizing( size ), LOAD_FACTOR );
	}

	/**
	 * Build a linked set whose size is unknown.
	 *
	 * @return The linked set.
	 */
	public static <K> LinkedHashSet<K> linkedSet() {
		return new LinkedHashSet<>();
	}

	/**
	 * Given a map, determine the proper initial size for a new Map to hold the same number of values.
	 * Specifically we want to account for load size and load factor to prevent immediate resizing.
	 *
	 * @param original The original map
	 *
	 * @return The proper size.
	 */
	public static int determineProperSizing(Map original) {
		return determineProperSizing( original.size() );
	}

	public static <X, Y> Map<X, Y> makeCopy(Map<X, Y> map) {
		final Map<X, Y> copy = mapOfSize( map.size() + 1 );
		copy.putAll( map );
		return copy;
	}

	public static <K, V> HashMap<K, V> makeCopy(
			Map<K, V> original,
			Function<K, K> keyTransformer,
			Function<V, V> valueTransformer) {
		if ( original == null ) {
			return null;
		}

		final HashMap<K, V> copy = new HashMap<>( determineProperSizing( original ) );

		original.forEach(
				(key, value) -> copy.put(
						keyTransformer.apply( key ),
						valueTransformer.apply( value )
				)
		);

		return copy;
	}

	public static <K, V> Map<K, V> makeMap(
			Collection<V> collection,
			Function<V,K> keyProducer) {
		return makeMap( collection, keyProducer, v -> v );
	}

	public static <K, V, E> Map<K, V> makeMap(
			Collection<E> collection,
			Function<E,K> keyProducer,
			Function<E,V> valueProducer) {
		if ( isEmpty( collection ) ) {
			return Collections.emptyMap();
		}

		final Map<K, V> map = new HashMap<>( determineProperSizing( collection.size() ));

		for ( E element : collection ) {
			map.put(
					keyProducer.apply( element ),
					valueProducer.apply( element )
			);
		}

		return map;
	}

	/**
	 * Given a set, determine the proper initial size for a new set to hold the same number of values.
	 * Specifically we want to account for load size and load factor to prevent immediate resizing.
	 *
	 * @param original The original set
	 *
	 * @return The proper size.
	 */
	public static int determineProperSizing(Set original) {
		return determineProperSizing( original.size() );
	}

	/**
	 * Determine the proper initial size for a new collection in order for it to hold the given a number of elements.
	 * Specifically we want to account for load size and load factor to prevent immediate resizing.
	 *
	 * @param numberOfElements The number of elements to be stored.
	 *
	 * @return The proper size.
	 */
	public static int determineProperSizing(int numberOfElements) {
		int actual = ( (int) ( numberOfElements / LOAD_FACTOR ) ) + 1;
		return Math.max( actual, MINIMUM_INITIAL_CAPACITY );
	}

	/**
	 * Create a properly sized {@link ConcurrentHashMap} based on the given expected number of elements.
	 *
	 * @param expectedNumberOfElements The expected number of elements for the created map
	 * @param <K> The map key type
	 * @param <V> The map value type
	 *
	 * @return The created map.
	 */
	public static <K, V> ConcurrentHashMap<K, V> concurrentMap(int expectedNumberOfElements) {
		return concurrentMap( expectedNumberOfElements, LOAD_FACTOR );
	}

	/**
	 * Create a properly sized {@link ConcurrentHashMap} based on the given expected number of elements and an
	 * explicit load factor
	 *
	 * @param expectedNumberOfElements The expected number of elements for the created map
	 * @param loadFactor The collection load factor
	 * @param <K> The map key type
	 * @param <V> The map value type
	 *
	 * @return The created map.
	 */
	public static <K, V> ConcurrentHashMap<K, V> concurrentMap(int expectedNumberOfElements, float loadFactor) {
		return new ConcurrentHashMap<>( expectedNumberOfElements, loadFactor );
	}

	public static <T> ArrayList<T> arrayList(int expectedNumberOfElements) {
		return new ArrayList<>( Math.max( expectedNumberOfElements + 1, DEFAULT_LIST_CAPACITY ) );
	}

	public static <T> Set<T> makeCopy(Set<T> source) {
		if ( source == null ) {
			return null;
		}

		final int size = source.size();
		final Set<T> copy = CollectionHelper.setOfSize( size + 1 );
		copy.addAll( source );
		return copy;
	}

	public static boolean isEmpty(Collection collection) {
		return collection == null || collection.isEmpty();
	}

	public static boolean isEmpty(Map map) {
		return map == null || map.isEmpty();
	}

	public static boolean isNotEmpty(Collection collection) {
		return !isEmpty( collection );
	}

	public static boolean isNotEmpty(Map map) {
		return !isEmpty( map );
	}

	public static boolean isEmpty(Object[] objects) {
		return objects == null || objects.length == 0;
	}

	public static boolean isNotEmpty(Object[] objects) {
		return objects != null && objects.length > 0;
	}

	public static <T> List<T> listOf(T value1) {
		final List<T> list = new ArrayList<>( 1 );
		list.add( value1 );
		return list;
	}

	public static <T> List<T> listOf(T... values) {
		final List<T> list = new ArrayList<>( values.length );
		Collections.addAll( list, values );
		return list;
	}

	public static <T> Set<T> setOf(T... values) {
		final HashSet<T> set = new HashSet<>( determineProperSizing( values.length ) );
		Collections.addAll( set, values );
		return set;
	}

	public static Properties asProperties(Map<?,?> map) {
		if ( map instanceof Properties ) {
			return ( (Properties) map );
		}

		final Properties properties = new Properties();
		if ( isNotEmpty( map ) ) {
			properties.putAll( map );
		}

		return properties;
	}

	/**
	 * Use to convert sets which will be retained for a long time,
	 * such as for the lifetime of the Hibernate ORM instance.
	 * The returned Set might be immutable, but there is no guarantee of this:
	 * consider it immutable but don't rely on this.
	 * The goal is to save memory.
	 * @return will never return null, but might return an immutable collection.
	 */
	public static <T> Set<T> toSmallSet(Set<T> set) {
		switch ( set.size() ) {
			case 0:
				return Collections.EMPTY_SET;
			case 1:
				return Collections.singleton( set.iterator().next() );
			default:
				//TODO assert tests pass even if this is set to return an unmodifiable Set
				return set;
		}
	}

	/**
	 * Use to convert Maps which will be retained for a long time,
	 * such as for the lifetime of the Hibernate ORM instance.
	 * The returned Map might be immutable, but there is no guarantee of this:
	 * consider it immutable but don't rely on this.
	 * The goal is to save memory.
	 */
	public static <K, V> Map<K, V> toSmallMap(final Map<K, V> map) {
		switch ( map.size() ) {
			case 0:
				return Collections.EMPTY_MAP;
			case 1:
				Map.Entry<K, V> entry = map.entrySet().iterator().next();
				return Collections.singletonMap( entry.getKey(), entry.getValue() );
			default:
				//TODO assert tests pass even if this is set to return an unmodifiable Map
				return map;
		}
	}

	/**
	 * Use to convert ArrayList instances which will be retained for a long time,
	 * such as for the lifetime of the Hibernate ORM instance.
	 * The returned List might be immutable, but there is no guarantee of this:
	 * consider it immutable but don't rely on this.
	 * The goal is to save memory.
	 */
	public static <V> List<V> toSmallList(ArrayList<V> arrayList) {
		switch ( arrayList.size() ) {
			case 0:
				return Collections.EMPTY_LIST;
			case 1:
				return Collections.singletonList( arrayList.get( 0 ) );
			default:
				arrayList.trimToSize();
				return arrayList;
		}
	}

	@SuppressWarnings( "unchecked" )
	public static <K,V> void collectMapEntries(BiConsumer<K, V> mapEntryConsumer, Object[] mappings) {
		// even numbered
		assert mappings.length % 2 == 0;

		for ( int i = 0; i < mappings.length; i += 2 ) {
			mapEntryConsumer.accept( (K) mappings[i], (V) mappings[i+1] );
		}
	}

	@SuppressWarnings( "unchecked" )
	public static <K,S> Map<K, S> asMap(Object[] elements) {
		assert elements != null;
		assert elements.length % 2 == 0;

		final HashMap<K,S> map = new HashMap<>();
		collectMapEntries( map::put, elements );
		for ( int i = 0; i < elements.length; i += 2 ) {
			map.put( (K) elements[ i ], (S) elements[ i+1 ] );
		}

		return map;
	}

	public static Map<String,String> toMap(String... pairs) {
		assert pairs.length % 2 == 0;
		if ( pairs.length == 2 ) {
			return Collections.singletonMap( pairs[0], pairs[1] );
		}

		final Map<String,String> result = new HashMap<>();
		applyToMap( result, pairs );
		return result;
	}

	private static void applyToMap(Map<String,String> map, String... pairs) {
		assert pairs.length % 2 == 0;
		for ( int i = 0; i < pairs.length; i+=2 ) {
			map.put( pairs[i], pairs[i+1] );
		}
	}

	public static Map<String,?> toMap(Object... pairs) {
		assert pairs.length % 2 == 0;
		if ( pairs.length == 2 ) {
			return Collections.singletonMap( (String) pairs[0], pairs[1] );
		}

		final Map<String,String> result = new HashMap<>();
		applyToMap( result, pairs );
		return result;
	}

	public static Map<String,Object> toSettingsMap(Object... pairs) {
		assert pairs.length % 2 == 0;
		if ( pairs.length == 2 ) {
			return Collections.singletonMap( (String) pairs[0], pairs[1] );
		}

		final Map<String,Object> result = new HashMap<>();
		applyToMap( result, pairs );
		return result;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static void applyToMap(Map<String,?> map, Object... pairs) {
		assert pairs.length % 2 == 0;
		for ( int i = 0; i < pairs.length; i+=2 ) {
			( (Map) map ).put( pairs[i], pairs[i+1] );
		}
	}

	public static String[] asPairs(Map<String,String> map) {
		final String[] pairs = new String[ map.size() * 2 ];
		int i = 0;
		for ( Map.Entry<String,String> entry : map.entrySet() ) {
			pairs[i++] = entry.getKey();
			pairs[i++] = entry.getValue();
		}
		return pairs;
	}

	public static Properties toProperties(Object... pairs) {
		final Properties properties = new Properties();
		if ( pairs.length > 0 ) {
			assert pairs.length % 2 == 0;
			for ( int i = 0; i < pairs.length; i+=2 ) {
				properties.put( pairs[i], pairs[i+1] );
			}
		}
		return properties;
	}

	public static void applyToProperties(Properties properties, Object... pairs) {
		assert pairs.length % 2 == 0;
		for ( int i = 0; i < pairs.length; i+=2 ) {
			properties.put( pairs[i], pairs[i+1] );
		}
	}

	public static <O> List<O> combine(List<O> list1, List<O> list2) {
		final ArrayList<O> combined = arrayList( list1.size() + list2.size() );
		combined.addAll( list1 );
		combined.addAll( list2 );
		return combined;
	}

	public static <O> List<O> combine(List<O>... lists) {
		final ArrayList<O> combined = new ArrayList<>();
		for ( int i = 0; i < lists.length; i++ ) {
			combined.addAll( lists[i] );
		}
		return combined;
	}

	public static int size(List<?> values) {
		return values == null ? 0 : values.size();
	}

	public static int size(Collection<?> values) {
		return values == null ? 0 : values.size();
	}

	public static int size(Map<?,?> values) {
		return values == null ? 0 : values.size();
	}

	public static <X> Set<X> toSet(X... values) {
		final HashSet<X> result = new HashSet<>();
		if ( isNotEmpty( values ) ) {
			result.addAll( Arrays.asList( values ) );
		}
		return result;
	}
}
