package com.fixtech.exception;

import com.fixtech.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> manejarNoEncontrado(
            NotFoundException ex, HttpServletRequest request) {
        return construir(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> manejarReglaDeNegocio(
            BusinessException ex, HttpServletRequest request) {
        return construir(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> manejarSolicitudInvalida(
            BadRequestException ex, HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> manejarNoAutorizado(
            UnauthorizedException ex, HttpServletRequest request) {
        return construir(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> manejarProhibido(
            ForbiddenException ex, HttpServletRequest request) {
        return construir(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> manejarAccesoDenegado(
            AccessDeniedException ex, HttpServletRequest request) {
        return construir(
                HttpStatus.FORBIDDEN,
                "No tiene permisos para realizar esta operación",
                request);
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ErrorResponse> manejarTokenInvalido(
            JwtException ex, HttpServletRequest request) {
        return construir(HttpStatus.UNAUTHORIZED, "Token inválido o expirado", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> manejarValidacion(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return construir(HttpStatus.BAD_REQUEST, "Datos de entrada inválidos", request, detalles);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> manejarRestriccionDeValidacion(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<String> detalles = ex.getConstraintViolations().stream()
                .map(violacion -> violacion.getPropertyPath() + ": " + violacion.getMessage())
                .toList();
        return construir(HttpStatus.BAD_REQUEST, "Datos de entrada inválidos", request, detalles);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> manejarValidacionDeParametros(
            HandlerMethodValidationException ex, HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, "Datos de entrada inválidos", request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> manejarFormatoInvalido(
            Exception ex, HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, "La solicitud no tiene el formato esperado", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> manejarTipoContenidoNoSoportado(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return construir(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "El tipo de contenido de la solicitud no es soportado",
                request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> manejarArchivoDemasiadoGrande(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return construir(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "El archivo excede el tamaño permitido",
                request);
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ErrorResponse> manejarMultipartInvalido(
            Exception ex, HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, "La solicitud multipart no es válida", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> manejarConflictoDeDatos(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Conflicto de integridad de datos en {} {}", request.getMethod(), request.getRequestURI());
        return construir(
                HttpStatus.CONFLICT, "La operación entra en conflicto con datos existentes", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> manejarRecursoInexistente(
            NoResourceFoundException ex, HttpServletRequest request) {
        return construir(HttpStatus.NOT_FOUND, "Recurso no encontrado", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarErrorNoControlado(
            Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado", request);
    }

    private ResponseEntity<ErrorResponse> construir(
            HttpStatus estado, String mensaje, HttpServletRequest request) {
        return construir(estado, mensaje, request, List.of());
    }

    private ResponseEntity<ErrorResponse> construir(
            HttpStatus estado, String mensaje, HttpServletRequest request, List<String> detalles) {
        ErrorResponse cuerpo = ErrorResponse.de(estado, mensaje, request.getRequestURI(), detalles);
        return ResponseEntity.status(estado).body(cuerpo);
    }
}
