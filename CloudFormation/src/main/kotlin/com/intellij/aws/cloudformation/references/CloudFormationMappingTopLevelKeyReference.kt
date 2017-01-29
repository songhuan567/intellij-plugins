package com.intellij.aws.cloudformation.references

import com.intellij.aws.cloudformation.CloudFormationParser
import com.intellij.aws.cloudformation.CloudFormationResolve
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.psi.PsiElement

class CloudFormationMappingTopLevelKeyReference(element: JsonStringLiteral, private val mappingName: String) : CloudFormationReferenceBase(element) {
  override fun resolve(): PsiElement? {
    val entityName = (myElement as JsonStringLiteral).value
    val parsed = CloudFormationParser.parse(myElement.containingFile)
    val firstLevelMappingKey = CloudFormationResolve.resolveFirstLevelMappingKey(parsed, mappingName, entityName) ?: return null
    return parsed.getPsiElement(firstLevelMappingKey)
  }

  override fun getCompletionVariants(): List<String> {
    val parsed = CloudFormationParser.parse(myElement.containingFile)
    return CloudFormationResolve.getTopLevelMappingKeys(parsed, mappingName) ?: emptyList()
  }
}
