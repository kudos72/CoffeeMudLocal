package com.planet_ink.coffee_mud.Abilities.Spells;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.interfaces.ItemPossessor.Expire;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.ItemCraftor.ItemKeyPair;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import java.util.*;

/*
   Copyright 2001-2014 Bo Zimmerman

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

	   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
@SuppressWarnings("rawtypes")
public class Spell_Fabricate extends Spell
{
	@Override public String ID() { return "Spell_Fabricate"; }
	private final static String localizedName = CMLib.lang().L("Fabricate");
	@Override public String name() { return localizedName; }
	@Override protected int canAffectCode(){return CAN_ITEMS;}
	@Override protected int canTargetCode(){return CAN_ITEMS;}
	@Override public int classificationCode(){ return Ability.ACODE_SPELL|Ability.DOMAIN_ALTERATION;}
	@Override public int abstractQuality(){ return Ability.QUALITY_INDIFFERENT;}
	
	protected static List<ItemCraftor> craftingSkills=new Vector<ItemCraftor>();
	protected static List<ItemCraftor> getCraftingSkills()
	{
		if(craftingSkills.size()==0)
		{
			final Vector<Ability> V=new Vector<Ability>();
			for(final Enumeration<Ability> e=CMClass.abilities();e.hasMoreElements();)
			{
				final Ability A=e.nextElement();
				if(A instanceof ItemCraftor)
					V.addElement((ItemCraftor)A.copyOf());
			}
			while(V.size()>0)
			{
				int lowest=Integer.MAX_VALUE;
				ItemCraftor lowestA=null;
				for(int i=0;i<V.size();i++)
				{
					final ItemCraftor A=(ItemCraftor)V.elementAt(i);
					final int ii=CMLib.ableMapper().lowestQualifyingLevel(A.ID());
					if(ii<lowest)
					{
						lowest=ii;
						lowestA=A;
					}
				}
				if(lowestA==null)
					lowestA=(ItemCraftor)V.firstElement();
				if(lowestA!=null)
				{
					V.removeElement(lowestA);
					craftingSkills.add(lowestA);
				}
				else
					break;
			}
		}
		return craftingSkills;
	}

	@Override
	public void unInvoke()
	{
		final Physical affected = super.affected;
		super.unInvoke();
		if(canBeUninvoked() && (affected instanceof Item))
		{
			final Item item=(Item)affected;
			if(item.owner() instanceof Room)
				((Room)item.owner()).showHappens(CMMsg.MSG_OK_VISUAL, item, L("<S-NAME> vanishes!."));
			else
			if(item.owner() instanceof MOB)
				((MOB)item.owner()).tell(((MOB)item.owner()),item,null,L("<T-NAME> vanishes!"));
			affected.destroy();
		}
	}
	
	@Override
	public boolean invoke(MOB mob, Vector commands, Physical givenTarget, boolean auto, int asLevel)
	{
		if(commands.size()<1)
		{
			mob.tell(L("Fabricate what?"));
			return false;
		}
		String intoWhat=CMParms.combineQuoted(commands, 0);
		
		Item intoI = null;
		for(ItemCraftor A : getCraftingSkills())
		{
			List<List<String>> L = A.matchingRecipeNames(intoWhat, false);
			if((L!=null)&&(L.size()>0))
			{
				ItemKeyPair what=A.craftAnyItem(-1);
				if((what!=null)&&(what.item!=null))
				{
					intoI=what.item;
					break;
				}
			}
		}
		if(intoI == null)
			for(ItemCraftor A : getCraftingSkills())
			{
				List<List<String>> L = A.matchingRecipeNames(intoWhat, true);
				if((L!=null)&&(L.size()>0))
				{
					ItemKeyPair what=A.craftAnyItem(-1);
					if((what!=null)&&(what.item!=null))
					{
						intoI=what.item;
						break;
					}
				}
			}
		if(intoI == null)
			intoI = mob.findItem(intoWhat);
		if(intoI == null)
			intoI = CMLib.map().findFirstRoomItem(mob.location().getArea().getCompleteMap(), mob, intoWhat, true, 5);
		if(intoI == null)
			intoI = CMLib.map().findFirstRoomItem(CMLib.map().rooms(), mob, intoWhat, true, 5);
		
		if(intoI == null)
		{
			mob.tell("You have no idea what a '"+intoWhat+"' is.  Perhaps if you saw one again?");
			return false;
		}
		
		if((intoI instanceof ArchonOnly)
		||(!CMLib.flags().isGettable(intoI))
		||(intoI instanceof ClanItem)
		||(intoI.basePhyStats().weight() > mob.maxCarry())
		||(CMath.bset(intoI.phyStats().sensesMask(), PhyStats.SENSE_ITEMNOWISH)))
		{
			mob.tell(L("You can't fabricate @x1!",intoI.Name()));
			return false;
		}
		
		// the reason this costs experience is to make it less valuable than Duplicate or Polymorph Object, 
		// but more valuable than Wish.
		final int experienceToLose=getXPCOSTAdjustment(mob,5+intoI.basePhyStats().level());
		CMLib.leveler().postExperience(mob,null,null,-experienceToLose,false);
		mob.tell(L("The effort causes you to lose @x1 experience.",""+experienceToLose));

		// the invoke method for spells receives as
		// parameters the invoker, and the REMAINING
		// command line parameters, divided into words,
		// and added as String objects to a vector.
		if(!super.invoke(mob,commands,givenTarget,auto,asLevel))
			return false;

		final boolean success=proficiencyCheck(mob,0,auto);

		if(success)
		{
			// it worked, so build a copy of this ability,
			// and add it to the affects list of the
			// affected MOB.  Then tell everyone else
			// what happened.
			invoker=mob;
			final CMMsg msg=CMClass.getMsg(mob,intoI,this,verbalCastCode(mob,intoI,auto),L("^S<S-NAME> wave(s) <S-HIS-HER> hands around, fabricating a @x1.^?",intoI.name()));
			if(mob.location().okMessage(mob,msg))
			{
				mob.location().send(mob,msg);
				if(msg.value()>0)
					return false;
				mob.addItem(intoI);
				beneficialAffect(mob,intoI,asLevel,0);
				mob.location().recoverRoomStats();
			}
		}
		else
			return beneficialWordsFizzle(mob,intoI,L("<S-NAME> attempt(s) to fabricate <T-NAME>, but flub(s) it.",intoWhat));

		// return whether it worked
		return success;
	}
}