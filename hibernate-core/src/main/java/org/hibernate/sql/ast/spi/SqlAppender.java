/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.ast.spi;

/**
 * Access to appending SQL fragments to an in-flight buffer
 *
 * @author Steve Ebersole
 */
@FunctionalInterface
public interface SqlAppender extends Appendable {
	String NO_SEPARATOR = "";
	String COMA_SEPARATOR = ",";
	char COMA_SEPARATOR_CHAR = ',';
	char WHITESPACE = ' ';

	char OPEN_PARENTHESIS = '(';
	char CLOSE_PARENTHESIS = ')';

	char PARAM_MARKER = '?';

	String NULL_KEYWORD = "null";

	/**
	 * Add the passed fragment into the in-flight buffer
	 */
	void appendSql(String fragment);

	default void appendSql(char fragment) {
		appendSql( Character.toString( fragment ) );
	}

	default void appendSql(int value) {
		appendSql( Integer.toString( value ) );
	}

	default void appendSql(long value) {
		appendSql( Long.toString( value ) );
	}

	default void appendSql(boolean value) {
		appendSql( String.valueOf( value ) );
	}

	default Appendable append(CharSequence csq) {
		appendSql( csq.toString() );
		return this;
	}

	default Appendable append(CharSequence csq, int start, int end) {
		appendSql( csq.toString().substring( start, end ) );
		return this;
	}

	default Appendable append(char c) {
		appendSql( Character.toString( c ) );
		return this;
	}

}
