
# TenantSettingsDto


## Properties

Name | Type
------------ | -------------
`byokEnabled` | boolean
`breakGlassApproverRequired` | boolean
`breakGlassMaxDurationMinutes` | number

## Example

```typescript
import type { TenantSettingsDto } from ''

// TODO: Update the object below with actual values
const example = {
  "byokEnabled": null,
  "breakGlassApproverRequired": null,
  "breakGlassMaxDurationMinutes": null,
} satisfies TenantSettingsDto

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TenantSettingsDto
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


