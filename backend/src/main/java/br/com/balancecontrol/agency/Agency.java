package br.com.balancecontrol.agency;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/** Public agency that places orders. Read-only in the application: maintained by the data load. */
@Entity
@Table(name = "agency")
public class Agency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String acronym;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String state;

    protected Agency() {
    }

    public Agency(String code, String acronym, String name, String state) {
        this.code = code;
        this.acronym = acronym;
        this.name = name;
        this.state = state;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getAcronym() {
        return acronym;
    }

    public String getName() {
        return name;
    }

    public String getState() {
        return state;
    }
}
