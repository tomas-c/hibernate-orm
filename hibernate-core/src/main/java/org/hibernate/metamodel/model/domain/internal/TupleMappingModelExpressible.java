/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.model.domain.internal;

import org.hibernate.cache.MutableCacheKeyBuilder;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.internal.util.IndexedConsumer;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.MappingModelExpressible;

/**
 * @author Christian Beikov
 */
public class TupleMappingModelExpressible implements MappingModelExpressible {

	private final MappingModelExpressible<Object>[] components;

	public TupleMappingModelExpressible(MappingModelExpressible<?>[] components) {
		this.components = (MappingModelExpressible<Object>[]) components;
	}

	@Override
	public int forEachJdbcType(int offset, IndexedConsumer<JdbcMapping> action) {
		int span = 0;
		for ( int i = 0; i < components.length; i++ ) {
			span += components[i].forEachJdbcType( offset + span, action );
		}
		return span;
	}

	@Override
	public Object disassemble(Object value, SharedSessionContractImplementor session) {
		if ( value == null ) {
			return null;
		}
		final Object[] disassembled = new Object[components.length];
		final Object[] array = (Object[]) value;
		for ( int i = 0; i < components.length; i++ ) {
			disassembled[i] = components[i].disassemble( array[i], session );
		}
		return disassembled;
	}

	@Override
	public void addToCacheKey(MutableCacheKeyBuilder cacheKey, Object value, SharedSessionContractImplementor session) {
		if ( value == null ) {
			for ( int i = 0; i < components.length; i++ ) {
				components[i].addToCacheKey( cacheKey, null, session );
			}
		}
		else {
			final Object[] array = (Object[]) value;
			for ( int i = 0; i < components.length; i++ ) {
				components[i].addToCacheKey( cacheKey, array[i], session );
			}
		}
	}

	@Override
	public <X, Y> int forEachDisassembledJdbcValue(
			Object value,
			int offset,
			X x,
			Y y,
			JdbcValuesBiConsumer<X, Y> valuesConsumer,
			SharedSessionContractImplementor session) {
		int span = 0;
		if ( value == null ) {
			for ( int i = 0; i < components.length; i++ ) {
				span += components[i].forEachDisassembledJdbcValue(
						null,
						span + offset,
						x,
						y,
						valuesConsumer,
						session
				);
			}
		}
		else {
			final Object[] values = (Object[]) value;
			for ( int i = 0; i < components.length; i++ ) {
				span += components[i].forEachDisassembledJdbcValue(
						values[i],
						span + offset,
						x,
						y,
						valuesConsumer,
						session
				);
			}
		}
		return span;
	}

	@Override
	public <X, Y> int forEachJdbcValue(
			Object value,
			int offset,
			X x,
			Y y,
			JdbcValuesBiConsumer<X, Y> valuesConsumer,
			SharedSessionContractImplementor session) {
		int span = 0;
		if ( value == null ) {
			for ( int i = 0; i < components.length; i++ ) {
				span += components[i].forEachDisassembledJdbcValue(
						components[i].disassemble( null, session ),
						span + offset,
						x, y, valuesConsumer,
						session
				);
			}
		}
		else {
			final Object[] values = (Object[]) value;
			for ( int i = 0; i < components.length; i++ ) {
				span += components[i].forEachDisassembledJdbcValue(
						components[i].disassemble( values[i], session ),
						span + offset,
						x, y, valuesConsumer,
						session
				);
			}
		}
		return span;
	}
}
