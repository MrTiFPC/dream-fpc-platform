package org.l2jmobius.gameserver.fakeplayer;

final class SocialSignalAssessment
{
	static final SocialSignalAssessment NONE = new SocialSignalAssessment("", 0, "", false, false, false, false, false, false, false, false, false, false, false, false, false, false);

	final String _focusEntityName;
	final int _positiveBondIntensity;
	final String _positiveBondSummary;
	final boolean _directThreat;
	final boolean _directFlame;
	final boolean _directDistrust;
	final boolean _directResentment;
	final boolean _negativeBeliefConflict;
	final boolean _positiveBondAlignment;
	final boolean _directApology;
	final boolean _directGratitude;
	final boolean _directAffection;
	final boolean _directRespect;
	final boolean _directPraise;
	final boolean _directReassurance;
	final boolean _directAbandonment;
	final boolean _repairAttempt;

	SocialSignalAssessment(String focusEntityName, int positiveBondIntensity, String positiveBondSummary, boolean directThreat, boolean directFlame, boolean directDistrust, boolean directResentment, boolean negativeBeliefConflict, boolean positiveBondAlignment, boolean directApology, boolean directGratitude, boolean directAffection, boolean directRespect, boolean directPraise, boolean directReassurance, boolean directAbandonment, boolean repairAttempt)
	{
		_focusEntityName = (focusEntityName == null) ? "" : focusEntityName;
		_positiveBondIntensity = positiveBondIntensity;
		_positiveBondSummary = (positiveBondSummary == null) ? "" : positiveBondSummary;
		_directThreat = directThreat;
		_directFlame = directFlame;
		_directDistrust = directDistrust;
		_directResentment = directResentment;
		_negativeBeliefConflict = negativeBeliefConflict;
		_positiveBondAlignment = positiveBondAlignment;
		_directApology = directApology;
		_directGratitude = directGratitude;
		_directAffection = directAffection;
		_directRespect = directRespect;
		_directPraise = directPraise;
		_directReassurance = directReassurance;
		_directAbandonment = directAbandonment;
		_repairAttempt = repairAttempt;
	}

	boolean hasNegativeSignal()
	{
		return _directThreat || _directFlame || _directDistrust || _directResentment || _negativeBeliefConflict || _directAbandonment;
	}

	boolean hasAnySignal()
	{
		return _directThreat
			|| _directFlame
			|| _directDistrust
			|| _directResentment
			|| _negativeBeliefConflict
			|| _positiveBondAlignment
			|| _directApology
			|| _directGratitude
			|| _directAffection
			|| _directRespect
			|| _directPraise
			|| _directReassurance
			|| _directAbandonment
			|| _repairAttempt;
	}
}
