package com.stocksanalyses.audit;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.hibernate.envers.Audited;

import jakarta.servlet.http.HttpServletRequest;

@Aspect
@Component
public class AuditAspect {

    @Autowired
    private AuditService auditService;

    @Before("@annotation(audited)")
    public void auditMethod(JoinPoint joinPoint, Audited audited) {
        try {
            HttpServletRequest request = getCurrentRequest();
            String methodName = joinPoint.getSignature().getName();
            String className = joinPoint.getTarget().getClass().getSimpleName();
            
            auditService.logAction(
                "METHOD_CALL", 
                className, 
                methodName, 
                request
            );
        } catch (Exception e) {
            // Don't fail the main operation if audit fails
            System.err.println("Audit aspect failed: " + e.getMessage());
        }
    }

    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
