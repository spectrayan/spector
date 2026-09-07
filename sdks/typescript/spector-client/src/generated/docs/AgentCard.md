
# AgentCard


## Properties

Name | Type
------------ | -------------
`name` | string
`description` | string
`version` | string
`capabilities` | Array&lt;string&gt;
`tools` | Array&lt;string&gt;
`inputModes` | Array&lt;string&gt;
`outputModes` | Array&lt;string&gt;
`endpoint` | string

## Example

```typescript
import type { AgentCard } from ''

// TODO: Update the object below with actual values
const example = {
  "name": null,
  "description": null,
  "version": null,
  "capabilities": null,
  "tools": null,
  "inputModes": null,
  "outputModes": null,
  "endpoint": null,
} satisfies AgentCard

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AgentCard
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


