/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.event.internal;

import org.hibernate.engine.spi.CascadingAction;
import org.hibernate.engine.spi.CascadingActions;
import org.hibernate.event.spi.PersistContext;

/**
 * When persist is used as the cascade action, persistOnFlush should be used
 * @author Emmanuel Bernard
 */
public class DefaultPersistOnFlushEventListener extends DefaultPersistEventListener {
	@Override
	protected CascadingAction<PersistContext> getCascadeAction() {
		return CascadingActions.PERSIST_ON_FLUSH;
	}
}
