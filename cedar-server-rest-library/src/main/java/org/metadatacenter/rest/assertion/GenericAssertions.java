package org.metadatacenter.rest.assertion;

public class GenericAssertions {
  public static final LoggedInAssertion LoggedIn = new LoggedInAssertion();
  public static final NonEmptyAssertion NonEmpty = new NonEmptyAssertion();
  public static final NullAssertion Null = new NullAssertion();
  public static final NonNullAssertion NonNull = new NonNullAssertion();
  public static final CedarAssertion JsonMergePatch = new JsonMergePatchAssertion();
  public static final CedarAssertion True = new TrueAssertion();
  public static final SuccessfulAssertion Successful = new SuccessfulAssertion();
  public static final ValidIdAssertion ValidId = new ValidIdAssertion();
  public static final ValidUrlAssertion ValidUrl = new ValidUrlAssertion();
  public static final ValidTemplateAssertion ValidTemplate = new ValidTemplateAssertion();
  public static final ValidElementAssertion ValidElement = new ValidElementAssertion();
  public static final ValidFieldAssertion ValidField = new ValidFieldAssertion();
}