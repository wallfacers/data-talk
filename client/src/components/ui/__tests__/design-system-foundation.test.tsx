import { render, screen } from "@testing-library/react"
import { describe, expect, it } from "vitest"

import { Button } from "@/components/ui/button"
import {
  InputGroup,
  InputGroupInput,
  InputGroupText,
} from "@/components/ui/input-group"
import {
  Table,
  TableBody,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"

describe("design system foundation", () => {
  it("renders tonal button semantics for secondary emphasis", () => {
    render(<Button variant="tonal">Tonal action</Button>)

    expect(screen.getByRole("button", { name: "Tonal action" })).toHaveClass(
      "bg-primary/10",
      "text-primary"
    )
  })

  it("renders input group as a panel-grade shell", () => {
    render(
      <InputGroup>
        <InputGroupText>Label</InputGroupText>
        <InputGroupInput aria-label="Search" />
      </InputGroup>
    )

    expect(screen.getByRole("group")).toHaveClass("rounded-2xl", "bg-bg-panel")
  })

  it("renders a structured table header and selected-row semantics", () => {
    render(
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Column</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow data-state="selected">
            <td>Value</td>
          </TableRow>
        </TableBody>
      </Table>
    )

    expect(screen.getByText("Column").closest("thead")).toHaveClass(
      "bg-muted/60"
    )
    expect(screen.getByText("Value").closest("tr")).toHaveClass(
      "data-[state=selected]:bg-primary/8"
    )
  })
})
