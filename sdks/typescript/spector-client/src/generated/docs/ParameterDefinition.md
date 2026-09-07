
# ParameterDefinition


## Properties

Name | Type
------------ | -------------
`name` | string
`displayName` | string
`description` | string
`type` | string
`required` | boolean
`defaultValue` | string
`placeholder` | string

## Example

```typescript
import type { ParameterDefinition } from ''

// TODO: Update the object below with actual values
const example = {
  "name": null,
  "displayName": null,
  "description": null,
  "type": null,
  "required": null,
  "defaultValue": null,
  "placeholder": null,
} satisfies ParameterDefinition

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ParameterDefinition
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


