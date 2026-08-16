package zw.co.hyperfeeds.support;
import org.springframework.stereotype.Component;
@Component class GuardedDraftAssistant implements LivestockGuidanceAssistant{public String draft(String subject,String question){return "DRAFT FOR EXPERT REVIEW — Gather the animal's age, symptoms, duration, feed and water intake, and recent treatments. If there is breathing difficulty, severe bleeding, inability to stand, poisoning, or rapid deterioration, contact a veterinarian immediately. This draft must not be shown as a diagnosis or treatment plan.";}}
