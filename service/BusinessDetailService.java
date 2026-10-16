package com.msme.cashflowguard.service;

import com.msme.cashflowguard.dto.BusinessDetailsRequest;
import com.msme.cashflowguard.dto.BusinessDetailsResponse;
import com.msme.cashflowguard.dto.UpcomingPaymentRequest;
import com.msme.cashflowguard.entity.AppUser;
import com.msme.cashflowguard.entity.BusinessDetail;
import com.msme.cashflowguard.entity.UpcomingPayment;
import com.msme.cashflowguard.exception.UnauthorizedException;
import com.msme.cashflowguard.repository.AppUserRepository;
import com.msme.cashflowguard.repository.BusinessDetailRepository;
import com.msme.cashflowguard.util.MoneyUtil;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The optional "Business details" page. Used by the forecast to add what a statement cannot show. */
@Service
public class BusinessDetailService {

    private final BusinessDetailRepository repository;
    private final AppUserRepository users;
    private final Clock clock;

    public BusinessDetailService(BusinessDetailRepository repository, AppUserRepository users, Clock clock) {
        this.repository = repository;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public BusinessDetailsResponse save(Long userId, BusinessDetailsRequest request) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Please login to continue."));
        BusinessDetail detail = repository.findByUserId(userId).orElseGet(() -> {
            BusinessDetail d = new BusinessDetail();
            d.setUser(user);
            return d;
        });
        detail.setMonthlyRent(request.monthlyRent() == null ? null : MoneyUtil.scale2(request.monthlyRent()));
        detail.setOtherFixedExpenses(request.otherFixedExpenses() == null ? null : MoneyUtil.scale2(request.otherFixedExpenses()));
        detail.setCashInHand(request.cashInHand() == null ? null : MoneyUtil.scale2(request.cashInHand()));
        detail.setUpdatedAt(LocalDateTime.now(clock));
        List<UpcomingPayment> payments = new ArrayList<>();
        if (request.upcomingPayments() != null) {
            for (UpcomingPaymentRequest p : request.upcomingPayments()) {
                payments.add(new UpcomingPayment(p.name().trim(), MoneyUtil.scale2(p.amount()), p.dueDate()));
            }
        }
        detail.getUpcomingPayments().clear();
        detail.getUpcomingPayments().addAll(payments);
        return toResponse(repository.save(detail), "Business details saved.");
    }

    @Transactional(readOnly = true)
    public BusinessDetailsResponse get(Long userId) {
        return repository.findByUserId(userId)
                .map(d -> toResponse(d, null))
                .orElse(new BusinessDetailsResponse(null, null, null, List.of(), null, "Nothing saved yet."));
    }

    private BusinessDetailsResponse toResponse(BusinessDetail d, String message) {
        List<UpcomingPaymentRequest> list = new ArrayList<>();
        for (UpcomingPayment p : d.getUpcomingPayments()) {
            list.add(new UpcomingPaymentRequest(p.getName(), p.getAmount(), p.getDueDate()));
        }
        return new BusinessDetailsResponse(d.getMonthlyRent(), d.getOtherFixedExpenses(), d.getCashInHand(),
                list, d.getUpdatedAt(), message);
    }
}
