import { Target, PiggyBank, Home, Car, Bike, Plane, GraduationCap, Heart, Gift, Briefcase, TrendingUp, Wallet } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

export const ICONES_META: { nome: string; Icone: LucideIcon }[] = [
  { nome: 'target',         Icone: Target },
  { nome: 'piggy-bank',     Icone: PiggyBank },
  { nome: 'home',           Icone: Home },
  { nome: 'car',            Icone: Car },
  { nome: 'bike',           Icone: Bike },
  { nome: 'plane',          Icone: Plane },
  { nome: 'graduation-cap', Icone: GraduationCap },
  { nome: 'heart',          Icone: Heart },
  { nome: 'gift',           Icone: Gift },
  { nome: 'briefcase',      Icone: Briefcase },
  { nome: 'trending-up',    Icone: TrendingUp },
  { nome: 'wallet',         Icone: Wallet },
]

export const MAPA_ICONES_META = Object.fromEntries(
  ICONES_META.map(({ nome, Icone }) => [nome, Icone])
) as Record<string, LucideIcon>
