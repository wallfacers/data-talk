# Builtin Semantic Model Templates

## Usage

Templates are copied to `~/.data-talk/semantic/<connectionId>/` when a user selects "Create from Template" in the `semantic_model_editor` tab.

## Field Conventions

- `name`: Domain name, must match `^[a-z][a-z0-9_]{0,63}$`
- `version`: Integer >= 1, bumped on structural changes
- `authored_by`: `ai_inferred` | `user_authored` | `hybrid`
- Every `dimension`, `measure`, and `metric` MUST have both `label_zh` and `label_en`

## Customization

1. Copy the template to `~/.data-talk/semantic/<connectionId>/<domain>.model.yaml`
2. Update `physical.table` names to match your actual schema
3. Add/remove dimensions, measures, and metrics as needed
4. Use `datatalk_semantic_propose_change` to propose modifications via AI

## Template List

| Template | Domains | Key Metrics |
|----------|---------|-------------|
| `ecommerce` | orders, users, products | GMV, AOV, Repurchase Rate |
| `saas` | subscriptions, users, events | MRR, ARR, Churn Rate, DAU, ARPU |
