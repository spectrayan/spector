
# ApprovalDecisionRequest


## Properties

Name | Type
------------ | -------------
`decision` | string
`modifiedArguments` | { [key: string]: any | null; }
`reason` | string

## Example

```typescript
import type { ApprovalDecisionRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "decision": null,
  "modifiedArguments": null,
  "reason": null,
} satisfies ApprovalDecisionRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ApprovalDecisionRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


