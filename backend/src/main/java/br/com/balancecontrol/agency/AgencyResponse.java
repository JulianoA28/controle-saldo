package br.com.balancecontrol.agency;

public record AgencyResponse(Long id, String code, String acronym, String name, String state) {

    public static AgencyResponse from(Agency agency) {
        return new AgencyResponse(agency.getId(), agency.getCode(), agency.getAcronym(), agency.getName(), agency.getState());
    }
}
