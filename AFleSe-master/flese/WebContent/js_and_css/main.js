function Toggle() {
	var x = document
			.getElementById("personalizationDivFuzzificationDefaultValues");
	if (x.style.display === "none" || x.style.display == "") {
		x.style.display = "block";
		setSlider();
	} else {
		x.style.display = "none";
	}
}

$(function(){
	setSlider();
});

function setSlider() {
	var slider = document.getElementById("defaultValue");
	var output = document.getElementById("defaultValueResult");
	if (slider != null) {
		output.value = slider.value;
		slider.oninput = function() {
			output.value = this.value;
			$("span#similarityMsg").html(getSimilarityState(output.value));
		}
	}
}

function getSimilarityState(val) {
	if(val == 0) {
		return "Completely different";
	} else if(val >= 0.1 && val <= 0.30) {
		return "Very different";
	} else if(val >= 0.31 && val <= 0.49) {
		return "Quite different";
	} else if(val == 0.50) {
		return "Similar";
	} else if(val >= 0.51 && val <= 0.99) {
		return "Very similar";
	} else if(val == 1) {
		return "Completely equal";
	}
}

function reloadOtherCombo() {
	var comboOne = document.getElementById("value1");
	var comboTwo = document.getElementById("value2");
	comboTwo.innerHTML = comboOne.innerHTML;
	if (comboOne.value != null && comboOne.value != '---') {
		for (var i = 0; i < comboTwo.options.length; i++) {
			if (comboTwo.options[i].text == comboOne.value) {
				comboTwo.remove(i);
				break;
			}
		}
	}
}

function validateCheckBox() {
	var checkBox = document.getElementById("toggleButton");
	if (checkBox.checked == false) {
		return false;
	} else {
		return true;
	}
}

function showDiv(evt, divId) {
    var i, tabcontent, tablinks;
    tabcontent = document.getElementsByClassName("tabcontent");
    for (i = 0; i < tabcontent.length; i++) {
        tabcontent[i].style.display = "none";
    }
    tablinks = document.getElementsByClassName("tablinks");
    for (i = 0; i < tablinks.length; i++) {
        tablinks[i].className = tablinks[i].className.replace(" active", "");
    }
    document.getElementById(divId).style.display = "block";
    evt.currentTarget.className += " active";
}