package uk.nhs.nhsx.virology.tokengen;

import uk.nhs.nhsx.virology.result.VirologyResultValidator;

import java.util.Objects;

public class CtaProcessorEvent {

    public final String testResult;
    public final String testEndDate;
    public final Integer numberOfTokens;

    public CtaProcessorEvent(String testResult, String testEndDate, Integer numberOfTokens) {
        VirologyResultValidator.validateTestResult(testResult,testEndDate);
        this.testResult = testResult;
        this.testEndDate = testEndDate;
        this.numberOfTokens = numberOfTokens;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CtaProcessorEvent that = (CtaProcessorEvent) o;
        return Objects.equals(testResult, that.testResult) &&
            Objects.equals(testEndDate, that.testEndDate) &&
            Objects.equals(numberOfTokens, that.numberOfTokens);
    }

    @Override
    public int hashCode() {
        return Objects.hash(testResult, testEndDate, numberOfTokens);
    }

    @Override
    public String toString() {
        return "CtaProcessorEvent{" +
            "testResult='" + testResult + '\'' +
            ", testEndDate='" + testEndDate + '\'' +
            ", numberOfTokens=" + numberOfTokens +
            '}';
    }
}
