/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.persister;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import org.hibernate.annotations.Persister;

/**
 * @author Shawn Clowater
 */
//tag::entity-persister-mapping[]

@Entity
@Persister(impl = EntityPersister.class)
public class Book {

    @Id
    public Integer id;

    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    public Author author;

    //Getters and setters omitted for brevity
    //end::entity-persister-mapping[]

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Author getAuthor() {
        return author;
    }

    public void setAuthor(Author author) {
        this.author = author;
    }
//tag::entity-persister-mapping[]
}
//end::entity-persister-mapping[]
