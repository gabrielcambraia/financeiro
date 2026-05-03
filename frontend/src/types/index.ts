export type AccountType = 'CHECKING' | 'SAVINGS' | 'WALLET' | 'INVESTMENT'
export type TransactionType = 'INCOME' | 'EXPENSE'
export type PaymentType = 'DEBIT' | 'CREDIT'

export interface Account {
  id: number
  name: string
  type: AccountType
  balance: number
  color: string
  icon: string
}

export interface Category {
  id: number
  name: string
  type: TransactionType
  color: string
  icon: string
}

export interface Transaction {
  id: number
  account: Account
  category?: Category
  accountId: number
  categoryId?: number
  type: TransactionType
  paymentType: PaymentType
  amount: number
  description?: string
  date: string
  fixed: boolean
  installmentTotal?: number
  installmentNumber?: number
  installmentGroupId?: string
}

export interface DashboardData {
  totalIncome: number
  totalExpense: number
  netBalance: number
  expensesByCategory: CategorySummary[]
  incomesByCategory: CategorySummary[]
  monthlyTrend: MonthlyTrend[]
  accountBalances: AccountBalance[]
  dailyBalance: DailyBalance[]
}

export interface CategorySummary {
  category: Category
  total: number
  percentage: number
}

export interface MonthlyTrend {
  month: string
  income: number
  expense: number
}

export interface AccountBalance {
  account: Account
  balance: number
}

export interface DailyBalance {
  date: string
  balance: number
}
