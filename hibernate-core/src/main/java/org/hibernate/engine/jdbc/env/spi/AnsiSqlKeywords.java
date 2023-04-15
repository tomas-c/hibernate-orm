/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.engine.jdbc.env.spi;

import java.util.List;

/**
 * Maintains the set of ANSI SQL keywords
 *
 * @author Steve Ebersole
 */
public final class AnsiSqlKeywords {

	private final List<String> keywordsSql2003;

	public AnsiSqlKeywords() {
		this.keywordsSql2003 = List.of(
				"add",
				"all",
				"allocate",
				"alter",
				"and",
				"any",
				"are",
				"array",
				"as",
				"asensitive",
				"asymmetric",
				"at",
				"atomic",
				"authorization",
				"begin",
				"between",
				"bigint",
				"blob",
				"binary",
				"both",
				"by",
				"call",
				"called",
				"cascaded",
				"case",
				"cast",
				"char",
				"character",
				"check",
				"clob",
				"close",
				"collate",
				"column",
				"commit",
				"condition",
				"connect",
				"constraint",
				"continue",
				"corresponding",
				"create",
				"cross",
				"cube",
				"current",
				"current_date",
				"current_path",
				"current_role",
				"current_time",
				"current_timestamp",
				"current_user",
				"cursor",
				"cycle",
				"date",
				"day",
				"deallocate",
				"dec",
				"decimal",
				"declare",
				"default",
				"delete",
				"deref",
				"describe",
				"deterministic",
				"disconnect",
				"distinct",
				"do",
				"double",
				"drop",
				"dynamic",
				"each",
				"element",
				"else",
				"elsif",
				"end",
				"escape",
				"except",
				"exec",
				"execute",
				"exists",
				"exit",
				"external",
				"false",
				"fetch",
				"filter",
				"float",
				"for",
				"foreign",
				"free",
				"from",
				"full",
				"function",
				"get",
				"global",
				"grant",
				"group",
				"grouping",
				"handler",
				"having",
				"hold",
				"hour",
				"identity",
				"if",
				"immediate",
				"in",
				"indicator",
				"inner",
				"inout",
				"input",
				"insensitive",
				"insert",
				"int",
				"integer",
				"intersect",
				"interval",
				"into",
				"is",
				"iterate",
				"join",
				"language",
				"large",
				"lateral",
				"leading",
				"leave",
				"left",
				"like",
				"local",
				"localtime",
				"localtimestamp",
				"loop",
				"match",
				"member",
				"merge",
				"method",
				"minute",
				"modifies",
				"module",
				"month",
				"multiset",
				"national",
				"natural",
				"nchar",
				"nclob",
				"new",
				"no",
				"none",
				"not",
				"null",
				"numeric",
				"of",
				"old",
				"on",
				"only",
				"open",
				"or",
				"order",
				"out",
				"outer",
				"output",
				"over",
				"overlaps",
				"parameter",
				"partition",
				"precision",
				"prepare",
				"primary",
				"procedure",
				"range",
				"reads",
				"real",
				"recursive",
				"ref",
				"references",
				"referencing",
				"release",
				"repeat",
				"resignal",
				"result",
				"return",
				"returns",
				"revoke",
				"right",
				"rollback",
				"rollup",
				"row",
				"rows",
				"savepoint",
				"scroll",
				"search",
				"second",
				"select",
				"sensitive",
				"session_use",
				"set",
				"signal",
				"similar",
				"smallint",
				"some",
				"specific",
				"specifictype",
				"sql",
				"sqlexception",
				"sqlstate",
				"sqlwarning",
				"start",
				"static",
				"submultiset",
				"symmetric",
				"system",
				"system_user",
				"table",
				"tablesample",
				"then",
				"time",
				"timestamp",
				"timezone_hour",
				"timezone_minute",
				"to",
				"trailing",
				"translation",
				"treat",
				"trigger",
				"true",
				"undo",
				"union",
				"unique",
				"unknown",
				"unnest",
				"until",
				"update",
				"user",
				"using",
				"value",
				"values",
				"varchar",
				"varying",
				"when",
				"whenever",
				"where",
				"while",
				"window",
				"with",
				"within",
				"without",
				"year");
	}

	/**
	 * Retrieve all keywords defined by ANSI SQL:2003
	 * All keywords returned by this implementation are in lowercase.
	 *
	 * @return ANSI SQL:2003 keywords
	 */
	public List<String> sql2003() {
		return keywordsSql2003;
	}

}
