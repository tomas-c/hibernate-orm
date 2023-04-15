/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.boot.model.process.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.Internal;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.jaxb.spi.BindableMappingDescriptor;
import org.hibernate.boot.jaxb.spi.Binding;
import org.hibernate.boot.model.convert.spi.ConverterDescriptor;
import org.hibernate.boot.model.process.spi.ManagedResources;
import org.hibernate.boot.spi.BootstrapContext;

import jakarta.persistence.AttributeConverter;

/**
 * @author Steve Ebersole
 */
public class ManagedResourcesImpl implements ManagedResources {
	private final Map<Class<? extends AttributeConverter>, ConverterDescriptor> attributeConverterDescriptorMap = new HashMap<>();
	private final Set<Class<?>> annotatedClassReferences = new LinkedHashSet<>();
	private final Set<String> annotatedClassNames = new LinkedHashSet<>();
	private final Set<String> annotatedPackageNames = new LinkedHashSet<>();
	private final List<Binding<BindableMappingDescriptor>> mappingFileBindings = new ArrayList<>();
	private Map<String, Class<?>> extraQueryImports;

	public static ManagedResourcesImpl baseline(MetadataSources sources, BootstrapContext bootstrapContext) {
		final ManagedResourcesImpl impl = new ManagedResourcesImpl();
		bootstrapContext.getAttributeConverters().forEach( impl::addAttributeConverterDefinition );
		impl.annotatedClassReferences.addAll( sources.getAnnotatedClasses() );
		impl.annotatedClassNames.addAll( sources.getAnnotatedClassNames() );
		impl.annotatedPackageNames.addAll( sources.getAnnotatedPackages() );
		impl.mappingFileBindings.addAll( sources.getXmlBindings() );
		impl.extraQueryImports = sources.getExtraQueryImports();
		return impl;
	}

	public ManagedResourcesImpl() {
	}

	@Override
	public Collection<ConverterDescriptor> getAttributeConverterDescriptors() {
		return Collections.unmodifiableCollection( attributeConverterDescriptorMap.values() );
	}

	@Override
	public Collection<Class<?>> getAnnotatedClassReferences() {
		return Collections.unmodifiableSet( annotatedClassReferences );
	}

	@Override
	public Collection<String> getAnnotatedClassNames() {
		return Collections.unmodifiableSet( annotatedClassNames );
	}

	@Override
	public Collection<String> getAnnotatedPackageNames() {
		return Collections.unmodifiableSet( annotatedPackageNames );
	}

	@Override
	public Collection<Binding<BindableMappingDescriptor>> getXmlMappingBindings() {
		return Collections.unmodifiableList( mappingFileBindings );
	}

	@Override
	public Map<String, Class<?>> getExtraQueryImports() {
		return extraQueryImports;
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// @Internal

	@Internal
	public void addAttributeConverterDefinition(ConverterDescriptor descriptor) {
		attributeConverterDescriptorMap.put( descriptor.getAttributeConverterClass(), descriptor );
	}

	@Internal
	public void addAnnotatedClassReference(Class<?> annotatedClassReference) {
		annotatedClassReferences.add( annotatedClassReference );
	}

	@Internal
	public void addAnnotatedClassName(String annotatedClassName) {
		annotatedClassNames.add( annotatedClassName );
	}

	@Internal
	public void addAnnotatedPackageName(String annotatedPackageName) {
		annotatedPackageNames.add( annotatedPackageName );
	}

	@Internal
	public void addXmlBinding(Binding<BindableMappingDescriptor> binding) {
		mappingFileBindings.add( binding );
	}
}
