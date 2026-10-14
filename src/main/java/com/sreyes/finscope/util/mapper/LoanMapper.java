package com.sreyes.finscope.util.mapper;

import com.sreyes.finscope.api.model.LoanInstallmentResponse;
import com.sreyes.finscope.api.model.LoanPaymentResponse;
import com.sreyes.finscope.api.model.LoanResponse;
import com.sreyes.finscope.model.entity.LoanPayment;
import com.sreyes.finscope.model.query.LoanDetail;
import com.sreyes.finscope.model.query.LoanInstallmentDetail;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper entre el préstamo y el modelo de préstamo del contrato OpenAPI.
 *
 * El origen es siempre {@link LoanDetail} y nunca la entidad: lo devuelto, lo que falta y el
 * estado no viven en la tabla, y un préstamo sin ellos no se responde en ningún sitio.
 *
 * Las correspondencias se declaran una a una a propósito: así, si alguna propiedad se
 * renombra, la compilación falla en lugar de dejar el campo silenciosamente a nulo.
 */
@Mapper(componentModel = "spring")
public interface LoanMapper {

  /**
   * Convierte un préstamo resuelto en su representación de respuesta.
   *
   * @param detail préstamo con su plan, sus pagos y sus cifras
   * @return la representación del préstamo
   */
  @Mapping(target = "id", source = "loan.id")
  @Mapping(target = "borrowerName", source = "loan.borrowerName")
  @Mapping(target = "principal", source = "loan.principal")
  @Mapping(target = "interest", source = "loan.interest")
  @Mapping(target = "currency", source = "loan.currency")
  @Mapping(target = "lentOn", source = "loan.lentOn")
  @Mapping(target = "notes", source = "loan.notes")
  @Mapping(target = "closedOn", source = "loan.closedOn")
  @Mapping(target = "total", source = "total")
  @Mapping(target = "paid", source = "paid")
  @Mapping(target = "outstanding", source = "outstanding")
  @Mapping(target = "overdueAmount", source = "overdueAmount")
  @Mapping(target = "status", source = "status")
  @Mapping(target = "nextDueDate", source = "nextDueDate")
  @Mapping(target = "nextDueAmount", source = "nextDueAmount")
  @Mapping(target = "installments", source = "installments")
  @Mapping(target = "payments", source = "payments")
  LoanResponse toResponse(LoanDetail detail);

  /**
   * Convierte una cuota con lo que la cubre en su representación de respuesta.
   *
   * @param installment cuota resuelta
   * @return la representación de la cuota
   */
  @Mapping(target = "id", source = "id")
  @Mapping(target = "number", source = "number")
  @Mapping(target = "dueDate", source = "dueDate")
  @Mapping(target = "amount", source = "amount")
  @Mapping(target = "paid", source = "paid")
  @Mapping(target = "status", source = "status")
  LoanInstallmentResponse toResponse(LoanInstallmentDetail installment);

  /**
   * Convierte un pago en su representación de respuesta.
   *
   * @param payment pago guardado
   * @return la representación del pago
   */
  @Mapping(target = "id", source = "id")
  @Mapping(target = "paidOn", source = "paidOn")
  @Mapping(target = "amount", source = "amount")
  @Mapping(target = "note", source = "note")
  LoanPaymentResponse toResponse(LoanPayment payment);
}
