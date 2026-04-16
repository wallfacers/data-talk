import { useState } from 'react'
import { EyeIcon, EyeOffIcon } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

interface PasswordInputProps extends Omit<React.ComponentProps<'input'>, 'type'> {
  showToggle?: boolean
}

export function PasswordInput({
  showToggle = true,
  className,
  ...props
}: PasswordInputProps) {
  const [show, setShow] = useState(false)

  if (!showToggle) {
    return <Input type="password" className={className} {...props} />
  }

  return (
    <div className="relative">
      <Input
        type={show ? 'text' : 'password'}
        className={cn('pr-10', className)}
        {...props}
      />
      <button
        type="button"
        onClick={() => setShow(!show)}
        className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
      >
        {show ? <EyeOffIcon className="size-4" /> : <EyeIcon className="size-4" />}
      </button>
    </div>
  )
}