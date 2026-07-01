package com.dlmp.loan.mapper;

import com.dlmp.loan.domain.entity.Loan;
import com.dlmp.loan.dto.response.LoanResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LoanMapper {

    @Mapping(target = "loanType", expression = "java(loan.getLoanType().name())")
    @Mapping(target = "status",   expression = "java(loan.getStatus().name())")
    LoanResponse toResponse(Loan loan);
}
