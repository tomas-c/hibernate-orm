/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.id.enhanced;

import org.hibernate.internal.util.ReflectHelper;

/**
 * @author Gavin King
 */
public class CustomOptimizerDescriptor implements OptimizerDescriptor {
	private final String className;

	CustomOptimizerDescriptor(String className) {
		this.className = className;
	}
	@Override
	public boolean isPooled() {
		return false;
	}

	@Override
	public String getExternalName() {
		return className;
	}

	@Override
	public Class<? extends Optimizer> getOptimizerClass() throws ClassNotFoundException {
		return ReflectHelper.classForName( className );
	}
}
