package com.tienda.pos.exception;

import com.tienda.pos.common.NormalMode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
@NormalMode
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String accessDenied(Model model) {
        model.addAttribute("message", "No tienes permiso para esta operacion.");
        return "error/403";
    }

    @ExceptionHandler(DomainException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String domain(DomainException ex, Model model) {
        model.addAttribute("message", ex.getMessage());
        return "error/400";
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String validation(MethodArgumentNotValidException ex, Model model) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "Revisa los datos enviados." : error.getDefaultMessage())
                .orElse("Revisa los datos enviados.");
        model.addAttribute("message", message);
        return "error/400";
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ModelAndView responseStatus(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        String template = switch (status) {
            case 400, 403, 404 -> "error/" + status;
            default -> "error/500";
        };
        ModelAndView view = new ModelAndView(template);
        view.setStatus(ex.getStatusCode());
        view.addObject("status", status);
        view.addObject("message", ex.getReason());
        return view;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String general(Exception ex, HttpServletRequest request, Model model) {
        log.error("Error inesperado en {}", request.getRequestURI(), ex);
        model.addAttribute("message", "Ocurrió un error inesperado. Revisa los logs para más detalle.");
        return "error/500";
    }
}
